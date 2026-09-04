package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Transacoes curtas da outbox; nenhuma delas chama o provedor externo. */
@Component
class PublicadorDaOutboxTransacoes {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDaOutboxTransacoes.class);

    private final Outbox outbox;
    private final MensagemRepositorio mensagens;
    private final MensagemIdExternoRepositorio idsExternos;
    private final LeadNoCaminhoDeMensagem leads;
    private final CanalGateway canal;
    private final OutboxProperties propriedades;
    private final ApplicationEventPublisher eventos;

    PublicadorDaOutboxTransacoes(
            Outbox outbox,
            MensagemRepositorio mensagens,
            MensagemIdExternoRepositorio idsExternos,
            LeadNoCaminhoDeMensagem leads,
            CanalGateway canal,
            OutboxProperties propriedades,
            ApplicationEventPublisher eventos) {
        this.outbox = outbox;
        this.mensagens = mensagens;
        this.idsExternos = idsExternos;
        this.leads = leads;
        this.canal = canal;
        this.propriedades = propriedades;
        this.eventos = eventos;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public List<Outbox.EnvioPendente> reservar(Instant agora) {
        return outbox.reservarPendentes(
                propriedades.lote(), agora, agora.plus(propriedades.reservaExpiracao()));
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void registrarResultado(
            Outbox.EnvioPendente pendente, ResultadoDeEnvio resultado, Instant quando) {
        switch (resultado) {
            case ResultadoDeEnvio.Aceito aceito -> {
                outbox.marcarPublicado(pendente.outboxId(), quando);
                mensagens.atualizarStatusEntrega(
                        pendente.mensagemId(), pendente.enviadoEm(), StatusEntrega.ENVIADO);
                idsExternos.gravar(
                        aceito.idExterno(),
                        pendente.mensagemId(),
                        pendente.enviadoEm(),
                        pendente.atendimentoId());
                eventos.publishEvent(new MudancaDeStatusDeEntrega(
                        pendente.mensagemId(),
                        pendente.atendimentoId(),
                        pendente.leadId(),
                        StatusEntrega.ENVIADO.name(),
                        quando));
                aprenderEnderecoDoProvedor(pendente, aceito);
                log.debug(
                        "Mensagem {} aceita pelo provedor {} como {}.",
                        pendente.mensagemId(),
                        canal.provedor(),
                        aceito.idExterno());
            }
            case ResultadoDeEnvio.Recusado recusado -> {
                int tentativasFeitas = pendente.tentativas() + 1;
                boolean desiste =
                        recusado.permanente() || tentativasFeitas >= propriedades.maximoDeTentativas();

                if (desiste) {
                    esgotar(pendente, quando, recusado, tentativasFeitas);
                } else {
                    outbox.reagendar(
                            pendente.outboxId(),
                            quando.plus(propriedades.esperaApos(pendente.tentativas())),
                            recusado.motivo());
                }
            }
        }
    }

    /**
     * Guarda o endereco que o provedor devolveu na resposta do envio, quando ele difere do
     * {@code to} que acabamos de usar.
     *
     * <p>Roda na mesma transacao do aceite — sem {@code REQUIRES_NEW}. Lead apagado so faz o
     * {@code UPDATE} afetar zero linhas (sem excecao); qualquer falha inesperada e engolida para
     * nao desfazer o {@code marcarPublicado} de um envio que a Meta ja aceitou. Sem evento de
     * timeline: e endereco de entrega, nao fato de negocio.
     */
    private void aprenderEnderecoDoProvedor(
            Outbox.EnvioPendente pendente, ResultadoDeEnvio.Aceito aceito) {
        String endereco = aceito.enderecoDoProvedor();
        if (endereco == null || endereco.isBlank()) {
            return;
        }
        if (endereco.equals(pendente.telefoneDestino())) {
            return;
        }
        try {
            leads.registrarTelefoneProvedor(pendente.leadId(), endereco);
            log.debug(
                    "Endereco do provedor aprendido para o lead {}: {} (destino enviado era {}).",
                    pendente.leadId(),
                    endereco,
                    pendente.telefoneDestino());
        } catch (RuntimeException e) {
            log.warn(
                    "Nao foi possivel gravar o endereco do provedor {} para o lead {} apos aceite "
                            + "da mensagem {}.",
                    endereco,
                    pendente.leadId(),
                    pendente.mensagemId(),
                    e);
        }
    }

    private void esgotar(
            Outbox.EnvioPendente pendente,
            Instant quando,
            ResultadoDeEnvio.Recusado recusado,
            int tentativasFeitas) {
        outbox.esgotar(pendente.outboxId(), quando, recusado.motivo());
        mensagens.atualizarStatusEntrega(
                pendente.mensagemId(), pendente.enviadoEm(), StatusEntrega.FALHOU);
        eventos.publishEvent(new MudancaDeStatusDeEntrega(
                pendente.mensagemId(),
                pendente.atendimentoId(),
                pendente.leadId(),
                StatusEntrega.FALHOU.name(),
                quando));

        log.error(
                "{} mensagem {} do lead {} nao foi entregue ao provedor {} apos {} tentativa(s). "
                        + "Motivo: {} ({}). A linha {} da outbox fica para inspecao e NAO sera "
                        + "reenviada sozinha.",
                PublicadorDaOutboxOperacoes.MARCADOR_ALARME,
                pendente.mensagemId(),
                pendente.leadId(),
                canal.provedor(),
                tentativasFeitas,
                recusado.motivo(),
                recusado.permanente() ? "recusa permanente" : "limite de tentativas",
                pendente.outboxId());
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public long contarEsgotadas() {
        return outbox.quantidadeEsgotada();
    }
}
