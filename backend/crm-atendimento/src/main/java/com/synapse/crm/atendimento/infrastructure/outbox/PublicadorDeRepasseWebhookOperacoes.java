package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.RepasseWebhookAutomacaoGateway;
import com.synapse.crm.atendimento.application.RepasseWebhookAutomacaoGateway.ResultadoRepasse;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Drena somente eventos de repasse; nunca ocupa o publisher do canal de saida. */
@Component
public class PublicadorDeRepasseWebhookOperacoes {

    public static final String MARCADOR_ALARME = "[ALERTA_REPASSE_AUTOMACAO_ESGOTADO]";

    private static final Logger log =
            LoggerFactory.getLogger(PublicadorDeRepasseWebhookOperacoes.class);

    private final Outbox outbox;
    private final RepasseWebhookAutomacaoGateway automacao;
    private final OutboxProperties propriedades;
    private final Clock relogio;

    public PublicadorDeRepasseWebhookOperacoes(
            Outbox outbox,
            RepasseWebhookAutomacaoGateway automacao,
            OutboxProperties propriedades,
            Clock relogio) {
        this.outbox = outbox;
        this.automacao = automacao;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public int rodada() {
        if (!automacao.configurado()) {
            return 0;
        }
        Instant agora = Instant.now(relogio);
        List<Outbox.RepasseWebhookPendente> pendentes =
                outbox.reservarRepassesWebhookPendentes(propriedades.lote(), agora);
        pendentes.forEach(pendente -> entregar(pendente, agora));
        return pendentes.size();
    }

    private void entregar(Outbox.RepasseWebhookPendente pendente, Instant agora) {
        ResultadoRepasse resultado =
                automacao.repassar(pendente.payloadCru(), pendente.assinatura());
        if (resultado == ResultadoRepasse.ACEITO) {
            outbox.marcarPublicado(pendente.outboxId(), agora);
            return;
        }

        int tentativasFeitas = pendente.tentativas() + 1;
        String erro = "Automacao indisponivel ou recusou o webhook";
        if (tentativasFeitas >= propriedades.maximoDeTentativas()) {
            outbox.esgotar(pendente.outboxId(), agora, erro);
            log.error(
                    "{} repasse {} desistiu apos {} tentativa(s); corpo e assinatura permanecem na outbox.",
                    MARCADOR_ALARME,
                    pendente.outboxId(),
                    tentativasFeitas);
        } else {
            outbox.reagendar(
                    pendente.outboxId(),
                    agora.plus(propriedades.esperaApos(pendente.tentativas())),
                    erro);
        }
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public long contarEsgotados() {
        return outbox.quantidadeRepassesWebhookEsgotados();
    }
}
