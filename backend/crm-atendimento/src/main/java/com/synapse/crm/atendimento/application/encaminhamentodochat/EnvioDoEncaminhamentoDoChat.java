package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.EventosCanonicosDeAtendimento;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.encaminhamentodochat.EncaminhamentoDoChatRepositorio.Encaminhamento;
import com.synapse.crm.atendimento.application.encaminhamentodochat.EncaminhamentoDoChatRepositorio.NovoEncaminhamento;
import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A parte transacional do encaminhamento, no pool do chat: envio pelo fluxo oficial, elo com a
 * mensagem interna e convite, numa transação só. Ou saem a mensagem, o elo e o convite, ou nenhum.
 *
 * <p>Bean separado do caso de uso de propósito: o caso de uso lê a mensagem interna no pool geral e
 * não pode ficar dentro desta transação, e a auditoria ({@code @Auditable}) precisa estar aqui, só no
 * caminho que de fato envia — repetir a mesma {@code Idempotency-Key} passa por {@link #repeticao} e
 * não grava segunda linha de auditoria.
 */
@Component
public class EnvioDoEncaminhamentoDoChat {

    private final EnviarMensagemUseCase enviar;
    private final AtendimentoRepositorio atendimentos;
    private final EncaminhamentoDoChatRepositorio encaminhamentos;
    private final ParticipacaoAtendimentoRepositorio participacoes;
    private final ApplicationEventPublisher eventos;
    private final UsuarioContext usuario;
    private final Clock relogio;

    public EnvioDoEncaminhamentoDoChat(
            EnviarMensagemUseCase enviar,
            AtendimentoRepositorio atendimentos,
            EncaminhamentoDoChatRepositorio encaminhamentos,
            ParticipacaoAtendimentoRepositorio participacoes,
            ApplicationEventPublisher eventos,
            UsuarioContext usuario,
            Clock relogio) {
        this.enviar = enviar;
        this.atendimentos = atendimentos;
        this.encaminhamentos = encaminhamentos;
        this.participacoes = participacoes;
        this.eventos = eventos;
        this.usuario = usuario;
        this.relogio = relogio;
    }

    /** O mesmo clique chegando de novo: devolve o que já foi feito, sem tocar em atendimento nem em convite. */
    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.responder')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Optional<EncaminhamentoDoChatParaCliente> repeticao(Comando comando) {
        return encaminhamentos.porChave(comando.chave()).map(existente -> reutilizar(existente, comando));
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.responder')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    @Auditable(acao = "ENCAMINHAR_CHAT_INTERNO_PARA_CLIENTE", entidadeTipo = "CHAT_INTERNO_ENCAMINHAMENTO_CLIENTE")
    public EncaminhamentoDoChatParaCliente executar(Comando comando, ConteudoDeEnvio conteudo) {
        UUID usuarioId = usuario.atual().id();
        // Sob a RLS de quem pede: atendimento que ele não alcança responde como inexistente.
        Atendimento destino = atendimentos
                .porId(comando.atendimentoId())
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", comando.atendimentoId()));

        EnviarMensagemUseCase.Resultado envio = enviar.executarEncaminhamentoDoChatInterno(
                destino.leadId(), destino.id(), conteudo, comando.chave());
        if (envio.reutilizadoIdempotente()) {
            // A chave já foi usada por um envio comum, não por um encaminhamento deste chat.
            return encaminhamentos
                    .porChave(comando.chave())
                    .map(existente -> reutilizar(existente, comando))
                    .orElseThrow(() -> new ChaveIdempotenciaReutilizadaException(
                            comando.chave(), "encaminhamento do chat interno", comando.atendimentoId()));
        }

        boolean conviteCriado = convidarSeNaoParticipa(envio.atendimento(), usuarioId);
        Mensagem mensagem = envio.mensagem();
        Encaminhamento registrado = encaminhamentos.registrar(new NovoEncaminhamento(
                comando.chave(),
                usuarioId,
                comando.conversaId(),
                comando.mensagemId(),
                destino.id(),
                destino.leadId(),
                mensagem.id(),
                mensagem.enviadoEm(),
                tipoDe(conteudo).name(),
                envio.transferiuOLead(),
                conviteCriado));
        return resultado(registrado, mensagem.statusEntrega(), false);
    }

    /**
     * Quem encaminha, sem ser o responsável nem já participar, recebe um convite para o atendimento
     * (docs/51); o responsável continua o mesmo. Convite pendente não é duplicado, e quem já participa
     * não recebe outro.
     */
    private boolean convidarSeNaoParticipa(Atendimento atendimento, UUID usuarioId) {
        UUID responsavel = atendimento.atendenteId();
        if (responsavel == null
                || responsavel.equals(usuarioId)
                || participacoes.eParticipanteAtivo(atendimento.id(), usuarioId)) {
            return false;
        }
        Instant agora = Instant.now(relogio);
        participacoes.expirarConvitesVencidos(
                atendimento.id(), usuarioId, agora.minus(participacoes.validadeConfigurada()));
        ParticipacaoAtendimentoRepositorio.ConviteResultado convite =
                participacoes.convidar(atendimento.id(), usuarioId, agora);
        if (convite.pedidoId() == null || !convite.criado()) {
            return false;
        }
        eventos.publishEvent(new EventoDeAtendimento.ConviteParaAtendimentoCriado(
                atendimento.leadId(), atendimento.id(), usuarioId, usuarioId, agora));
        EventosCanonicosDeAtendimento.publicar(
                atendimentos,
                eventos,
                EventoCanonicoDeAtendimento.Tipo.CONVITE_ATENDIMENTO_CRIADO,
                atendimento.id(),
                atendimento.leadId(),
                agora);
        return true;
    }

    private EncaminhamentoDoChatParaCliente reutilizar(Encaminhamento existente, Comando comando) {
        if (!existente.usuarioId().equals(usuario.atual().id())
                || !existente.mensagemInternaId().equals(comando.mensagemId())
                || !existente.atendimentoId().equals(comando.atendimentoId())) {
            throw new ChaveIdempotenciaReutilizadaException(
                    comando.chave(), "encaminhamento do chat interno", existente.atendimentoId());
        }
        StatusEntrega status = encaminhamentos
                .statusDaMensagemExterna(existente.mensagemExternaId(), existente.mensagemExternaEnviadaEm())
                .orElse(StatusEntrega.PENDENTE);
        return resultado(existente, status, true);
    }

    private static EncaminhamentoDoChatParaCliente resultado(
            Encaminhamento registro, StatusEntrega status, boolean reutilizado) {
        return new EncaminhamentoDoChatParaCliente(
                registro.id(),
                registro.usuarioId(),
                registro.conversaId(),
                registro.mensagemInternaId(),
                registro.atendimentoId(),
                registro.leadId(),
                registro.mensagemExternaId(),
                registro.mensagemExternaEnviadaEm(),
                registro.tipo(),
                registro.transferiuOLead(),
                registro.conviteCriado(),
                status.name(),
                reutilizado);
    }

    private static TipoMensagem tipoDe(ConteudoDeEnvio conteudo) {
        return conteudo instanceof ConteudoDeEnvio.MensagemMidia midia ? midia.tipo() : TipoMensagem.TEXTO;
    }

    /** Identifica o clique: nada daqui vem do conteúdo da mensagem, e o destino é validado contra o alcance do usuário. */
    public record Comando(UUID conversaId, UUID mensagemId, UUID atendimentoId, String chave) {}
}
