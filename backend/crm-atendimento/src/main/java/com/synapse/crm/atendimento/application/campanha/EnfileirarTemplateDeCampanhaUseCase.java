package com.synapse.crm.atendimento.application.campanha;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.origem.OrigemDaMensagem;
import com.synapse.crm.atendimento.application.origem.RegistroDeOrigemDaMensagem;
import com.synapse.crm.atendimento.application.origem.TipoDeOrigem;
import com.synapse.crm.atendimento.application.proativo.ReservaDeEnvioProativoRepositorio;
import com.synapse.crm.atendimento.application.proativo.ReservarEnvioProativoUseCase;
import com.synapse.crm.atendimento.application.proativo.ReservarEnvioProativoUseCase.Decisao;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Enfileira o template de UM destinatario de campanha (E220), na mesma transacao da reserva da politica
 * proativa (E219).
 *
 * <p>Nao e o envio humano. {@code EnviarMensagemUseCase} abre atendimento, atribui dono ao lead (RN-CRM-02
 * e RN-CRM-06, a comissao do atendente) e o deixa EM_ATENDIMENTO; numa campanha para milhares de contatos
 * isso entregaria a base inteira a quem criou a campanha e encheria as filas. Aqui a mensagem fica num
 * atendimento ja FINALIZADO e sem atendente: nao aparece em fila nenhuma e nao troca dono. O historico da
 * conversa e por lead, entao quem atende a resposta ve o template; a resposta do cliente abre um
 * atendimento novo pelo fluxo normal (IA).
 *
 * <p>Decisao deliberada: nao toca {@code ultima_interacao_em} nem {@code num_mensagens} do lead. Uma
 * campanha reescrevendo a "ultima interacao" da Agenda inteira esconderia justamente os contatos
 * dormentes que ela quer reativar.
 */
@Service
public class EnfileirarTemplateDeCampanhaUseCase {

    private final LeadNoCaminhoDeMensagem leads;
    private final AtendimentoRepositorio atendimentos;
    private final MensagemRepositorio mensagens;
    private final Outbox outbox;
    private final ReservarEnvioProativoUseCase reservas;
    private final ReservaDeEnvioProativoRepositorio repositorioDeReservas;
    private final RegistroDeOrigemDaMensagem origens;
    private final Clock relogio;

    public EnfileirarTemplateDeCampanhaUseCase(
            LeadNoCaminhoDeMensagem leads,
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            Outbox outbox,
            ReservarEnvioProativoUseCase reservas,
            ReservaDeEnvioProativoRepositorio repositorioDeReservas,
            RegistroDeOrigemDaMensagem origens,
            Clock relogio) {
        this.leads = leads;
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.outbox = outbox;
        this.reservas = reservas;
        this.repositorioDeReservas = repositorioDeReservas;
        this.origens = origens;
        this.relogio = relogio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(Pedido pedido) {
        UUID leadId = pedido.leadId();
        Instant agora = Instant.now(relogio);

        // Lock do lead antes de olhar o atendimento: serializa com quem esteja abrindo conversa agora.
        if (!leads.bloquearParaAtendimento(leadId)) {
            return Resultado.recusado(Motivo.LEAD_INDISPONIVEL, null);
        }
        Optional<LeadNoCaminhoDeMensagem.ContatoParaEnvio> contato = leads.contatoParaEnvio(leadId);
        if (contato.isEmpty()) {
            return Resultado.recusado(Motivo.LEAD_INDISPONIVEL, null);
        }
        // O envio de teste vai para um contato de teste explicitamente autorizado: nao passa pela politica
        // proativa nem recusa quem esta em atendimento, e nao ocupa a ocorrencia da campanha de verdade.
        Decisao decisao = null;
        if (!pedido.teste()) {
            if (atendimentos.abertoDoLead(leadId).isPresent()) {
                return Resultado.recusado(Motivo.ATENDIMENTO_ABERTO, null);
            }
            decisao = reservas.reservarParaCampanha(leadId, pedido.campanhaId());
            if (!decisao.podeEnviar()) {
                return Resultado.recusado(traduzir(decisao.motivo()), decisao.liberadoApos());
            }
        }

        Atendimento atendimento = atendimentos.salvar(
                Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, agora).finalizar(agora));
        ConteudoDeEnvio.MensagemTemplate conteudo = new ConteudoDeEnvio.MensagemTemplate(
                pedido.nomeDoTemplate(), pedido.idioma(), pedido.parametros(), pedido.corpoRenderizado());
        Mensagem mensagem = mensagens.registrar(new Mensagem(
                UUID.randomUUID(),
                atendimento.id(),
                Remetente.ia(),
                TipoMensagem.TEXTO,
                conteudo.paraHistorico(),
                null,
                null,
                StatusEntrega.PENDENTE,
                agora));
        outbox.enfileirarEnvio(
                mensagem.id(),
                agora,
                atendimento.id(),
                leadId,
                contato.get().telefoneDestino(),
                atendimento.canalCredencialId(),
                conteudo);
        String regra = (pedido.teste() ? "teste:" : "") + pedido.campanhaId();
        origens.registrar(
                "campanha " + regra,
                mensagem.id(),
                atendimento.id(),
                leadId,
                agora,
                OrigemDaMensagem.deReservaProativa(TipoDeOrigem.CAMPANHA, regra, null));
        if (decisao != null) {
            repositorioDeReservas.concluir(decisao.reserva().chave(), mensagem.id(), null, agora);
        }
        return Resultado.enfileirado(mensagem.id(), agora, atendimento.id());
    }

    private static Motivo traduzir(ReservarEnvioProativoUseCase.Motivo motivo) {
        return switch (motivo) {
            case CHAVE_JA_USADA, OCORRENCIA_JA_REGISTRADA -> Motivo.JA_RESERVADO;
            case AUTOMACAO_PROATIVA_DESLIGADA -> Motivo.AUTOMACAO_PROATIVA_DESLIGADA;
            case TIPO_DESLIGADO -> Motivo.TIPO_PROATIVO_DESLIGADO;
            case COOLDOWN -> Motivo.COOLDOWN;
            case TETO_DIARIO -> Motivo.TETO_DIARIO_POR_LEAD;
        };
    }

    /**
     * @param parametros valores do corpo na ordem do template, ja resolvidos pela campanha
     * @param corpoRenderizado texto como o cliente o le, para o historico da conversa
     * @param teste envio de teste: sem politica proativa e sem recusa por atendimento aberto
     */
    public record Pedido(
            UUID campanhaId,
            UUID leadId,
            String nomeDoTemplate,
            String idioma,
            List<String> parametros,
            String corpoRenderizado,
            boolean teste) {}

    /** Por que o destinatario nao foi enfileirado. Nada disso e retentado: o destinatario sai da fila. */
    public enum Motivo {
        LEAD_INDISPONIVEL,
        ATENDIMENTO_ABERTO,
        JA_RESERVADO,
        AUTOMACAO_PROATIVA_DESLIGADA,
        TIPO_PROATIVO_DESLIGADO,
        COOLDOWN,
        TETO_DIARIO_POR_LEAD
    }

    public sealed interface Resultado permits Resultado.Enfileirado, Resultado.Recusado {

        static Resultado enfileirado(UUID mensagemId, Instant enviadoEm, UUID atendimentoId) {
            return new Enfileirado(mensagemId, enviadoEm, atendimentoId);
        }

        static Resultado recusado(Motivo motivo, Instant liberadoApos) {
            return new Recusado(motivo, liberadoApos);
        }

        record Enfileirado(UUID mensagemId, Instant enviadoEm, UUID atendimentoId) implements Resultado {}

        /** @param liberadoApos quando cooldown ou teto deixam de valer, se o motivo for um deles */
        record Recusado(Motivo motivo, Instant liberadoApos) implements Resultado {}
    }
}
