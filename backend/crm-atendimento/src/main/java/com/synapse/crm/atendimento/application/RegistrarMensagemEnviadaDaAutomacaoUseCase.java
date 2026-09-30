package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Registra no CRM o envio que a Automação já concluiu no provedor. */
@Service
public class RegistrarMensagemEnviadaDaAutomacaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrarMensagemEnviadaDaAutomacaoUseCase.class);
    private static final int TAMANHO_MAXIMO_DA_CHAVE_DE_ENVIO = 200;

    private final AtendimentoRepositorio atendimentos;
    private final MensagemRepositorio mensagens;
    private final IdempotenciaDeMensagemAutomacaoRepositorio idempotencia;
    private final ReservaDeEnvioDaAutomacaoRepositorio reservasDeEnvio;
    private final MensagemIdExternoRepositorio idsExternos;
    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public RegistrarMensagemEnviadaDaAutomacaoUseCase(
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            IdempotenciaDeMensagemAutomacaoRepositorio idempotencia,
            ReservaDeEnvioDaAutomacaoRepositorio reservasDeEnvio,
            MensagemIdExternoRepositorio idsExternos,
            LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.idempotencia = idempotencia;
        this.reservasDeEnvio = reservasDeEnvio;
        this.idsExternos = idsExternos;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID atendimentoId, Requisicao requisicao) {
        validar(requisicao);
        Atendimento atendimento = atendimentos.porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        Instant agora = Instant.now(relogio);
        // Antes de qualquer escrita: um conflito aborta a transacao sem registrar a mensagem.
        if (requisicao.chaveDeEnvio() != null) {
            concluirReservaDeEnvio(atendimentoId, requisicao, agora);
        }
        UUID mensagemId = UUID.randomUUID();
        IdempotenciaDeMensagemAutomacaoRepositorio.Reserva reserva = idempotencia.reservar(
                requisicao.wamid(), atendimentoId, mensagemId, agora);

        if (!reserva.nova()) {
            if (!reserva.atendimentoId().equals(atendimentoId)) {
                throw new WamidJaRegistradoEmOutroAtendimentoException(requisicao.wamid(), atendimentoId);
            }
            return new Resultado(atendimentoId, reserva.mensagemId(), StatusEntrega.ENVIADO, reserva.enviadoEm(), true);
        }

        Mensagem mensagem = criarMensagem(atendimento, requisicao, mensagemId, agora);
        mensagens.registrar(mensagem);
        idsExternos.gravar(requisicao.wamid(), mensagem.id(), mensagem.enviadoEm(), atendimento.id());
        leads.registrarInteracao(atendimento.leadId(), agora, 0, 1);
        EventosCanonicosDeAtendimento.publicar(
                atendimentos,
                eventos,
                EventoCanonicoDeAtendimento.Tipo.MENSAGEM_ENVIADA_AUTOMACAO,
                atendimento.id(),
                atendimento.leadId(),
                agora);
        eventos.publishEvent(new MensagemParaTempoReal(
                atendimento.id(),
                atendimento.leadId(),
                mensagem.id(),
                mensagem.remetente().tipo().name(),
                mensagem.remetente().id(),
                mensagem.tipo().name(),
                mensagem.conteudo(),
                mensagem.midiaUrl(),
                mensagem.midiaMetadados(),
                mensagem.opcoes(),
                mensagem.statusEntrega().name(),
                mensagem.enviadoEm()));
        return new Resultado(atendimentoId, mensagem.id(), mensagem.statusEntrega(), mensagem.enviadoEm(), false);
    }

    /**
     * Fecha a reserva feita antes do envio (docs/50). E tambem o caminho de recuperacao quando o
     * provedor aceitou mas o registro falhou: repetir esta chamada com o mesmo wamid e a mesma chave
     * registra a saida sem que nada seja reenviado.
     */
    private void concluirReservaDeEnvio(UUID atendimentoId, Requisicao requisicao, Instant agora) {
        String chave = requisicao.chaveDeEnvio();
        if (reservasDeEnvio.concluir(chave, atendimentoId, requisicao.wamid(), agora)) {
            return;
        }
        Optional<ReservaDeEnvioDaAutomacaoRepositorio.Reserva> existente = reservasDeEnvio.buscar(chave);
        if (existente.isEmpty()) {
            // A mensagem ja saiu: perder o historico seria pior. Fica o alerta para o fluxo corrigir.
            log.warn("Saida da Automacao registrada com chaveDeEnvio sem reserva: {} (atendimento {}).", chave, atendimentoId);
            return;
        }
        ReservaDeEnvioDaAutomacaoRepositorio.Reserva reserva = existente.get();
        if (!reserva.atendimentoId().equals(atendimentoId)) {
            throw new ReservaDeEnvioConflitanteException("a chave de envio pertence a outro atendimento");
        }
        if (!requisicao.wamid().equals(reserva.wamidSaida())) {
            throw new ReservaDeEnvioConflitanteException(
                    "a reserva desta chave ja foi concluida com outro wamid: houve um segundo envio");
        }
        // Mesma chave e mesmo wamid: repeticao idempotente do registro.
    }

    private static void validar(Requisicao requisicao) {
        if (requisicao == null || requisicao.wamid() == null || requisicao.wamid().isBlank()) {
            throw new MensagemAutomacaoInvalidaException("wamid e obrigatorio");
        }
        if (requisicao.chaveDeEnvio() != null
                && (requisicao.chaveDeEnvio().isBlank()
                        || requisicao.chaveDeEnvio().length() > TAMANHO_MAXIMO_DA_CHAVE_DE_ENVIO)) {
            throw new MensagemAutomacaoInvalidaException("chaveDeEnvio vazia ou maior que 200 caracteres");
        }
        if (requisicao.tipo() == null) {
            throw new MensagemAutomacaoInvalidaException("tipo e obrigatorio");
        }
        if (requisicao.tipo() == TipoMensagem.TEXTO
                && (requisicao.conteudo() == null || requisicao.conteudo().isBlank())) {
            throw new MensagemAutomacaoInvalidaException("conteudo e obrigatorio para TEXTO");
        }
        if (requisicao.tipo() != TipoMensagem.TEXTO
                && !requisicao.tipo().exigeOpcoes()
                && (requisicao.midiaUrl() == null || requisicao.midiaUrl().isBlank())) {
            throw new MensagemAutomacaoInvalidaException("midiaUrl e obrigatorio para mensagens de midia");
        }
        if (requisicao.tipo().exigeOpcoes()
                && (requisicao.opcoes() == null || requisicao.opcoes().isBlank())) {
            throw new MensagemAutomacaoInvalidaException("opcoes e obrigatorio para mensagens interativas");
        }
    }

    private static Mensagem criarMensagem(
            Atendimento atendimento, Requisicao requisicao, UUID mensagemId, Instant agora) {
        if (requisicao.tipo() == TipoMensagem.TEXTO) {
            return Mensagem.texto(mensagemId, atendimento.id(), Remetente.ia(), requisicao.conteudo(), agora);
        }
        if (requisicao.tipo().exigeOpcoes()) {
            return Mensagem.interativa(
                    mensagemId,
                    atendimento.id(),
                    Remetente.ia(),
                    requisicao.tipo(),
                    requisicao.conteudo(),
                    requisicao.opcoes(),
                    agora);
        }
        return Mensagem.midia(
                mensagemId,
                atendimento.id(),
                Remetente.ia(),
                requisicao.tipo(),
                requisicao.midiaUrl(),
                requisicao.midiaMetadados(),
                agora);
    }

    /** {@code chaveDeEnvio}: a chave reservada antes do envio (opcional; nula = fluxo antigo). */
    public record Requisicao(
            String wamid,
            TipoMensagem tipo,
            String conteudo,
            String midiaUrl,
            String midiaMetadados,
            String opcoes,
            String chaveDeEnvio) {

        public Requisicao(
                String wamid, TipoMensagem tipo, String conteudo, String midiaUrl, String midiaMetadados, String opcoes) {
            this(wamid, tipo, conteudo, midiaUrl, midiaMetadados, opcoes, null);
        }
    }

    public record Resultado(
            UUID atendimentoId,
            UUID mensagemId,
            StatusEntrega statusEntrega,
            Instant enviadoEm,
            boolean idempotente) {}
}
