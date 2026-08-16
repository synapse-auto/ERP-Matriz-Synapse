package com.synapse.crm.atendimento.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Ponto de entrada agendado do repasse assíncrono para a Automacao. */
@Component
public class PublicadorDeRepasseWebhook {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeRepasseWebhook.class);

    private final PublicadorDeRepasseWebhookOperacoes operacoes;

    public PublicadorDeRepasseWebhook(PublicadorDeRepasseWebhookOperacoes operacoes) {
        this.operacoes = operacoes;
    }

    @Scheduled(fixedDelayString = "${synapse.automacao.repasse-webhook.intervalo-ms:1000}")
    public void publicarPendentes() {
        ContextoDeServico.executarComo("repasse-webhook-automacao", operacoes::rodada);
    }

    @Scheduled(cron = "${synapse.automacao.repasse-webhook.cron-alerta:0 */15 * * * *}")
    public void alertarSobreEsgotados() {
        ContextoDeServico.executarComo("alerta-repasse-webhook", () -> {
            long esgotados = operacoes.contarEsgotados();
            if (esgotados > 0) {
                log.error(
                        "{} {} repasse(s) para a Automacao esgotaram as tentativas. Inspecione outbox_evento com tipo='{}'.",
                        PublicadorDeRepasseWebhookOperacoes.MARCADOR_ALARME,
                        esgotados,
                        OutboxRepositorioJdbc.TIPO_REPASSE_WEBHOOK);
            }
        });
    }
}
