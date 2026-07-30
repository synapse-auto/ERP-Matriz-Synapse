package com.synapse.crm.atendimento.infrastructure.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Agendamento do drenador da fila de entrada. So dispara o job e declara o
 * {@link ContextoDeServico}; a logica transacional mora em {@link ProcessadorDeWebhookEntradaOperacoes},
 * injetada aqui.
 *
 * <p>As duas responsabilidades vivem em beans separados de proposito. Um {@code @Scheduled} que
 * chamasse {@code this::rodada} dentro da propria classe pularia o proxy CGLIB do Spring — a chamada
 * seria uma invocacao Java comum, sem interceptador nenhum, e {@code @Transactional} nao teria
 * efeito. Foi exatamente esse bug (E07b): webhooks nunca eram processados porque {@code rodada()}
 * lancava {@code AcessoSemTransacaoException} a cada tick, e o {@code @Scheduled} engolia a excecao e
 * so logava — sem nenhum teste chamando este metodo pelo bean do Spring, ninguem percebeu. Chamando
 * {@link ProcessadorDeWebhookEntradaOperacoes#rodada()} atraves da instancia injetada, a chamada sai
 * por fora e passa pelo proxy de verdade.
 */
@Component
public class ProcessadorDeWebhookEntrada {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeWebhookEntrada.class);

    private final ProcessadorDeWebhookEntradaOperacoes operacoes;

    public ProcessadorDeWebhookEntrada(ProcessadorDeWebhookEntradaOperacoes operacoes) {
        this.operacoes = operacoes;
    }

    @Scheduled(fixedDelayString = "${synapse.canal.webhook.intervalo-ms:1000}")
    public void processarPendentes() {
        ContextoDeServico.executarComo("processador-webhook", operacoes::rodada);
    }

    @Scheduled(cron = "${synapse.canal.webhook.cron-alerta:0 */15 * * * *}")
    public void alertarSobreEsgotados() {
        ContextoDeServico.executarComo("alerta-webhook", () -> {
            long esgotados = operacoes.contarEsgotados();
            if (esgotados > 0) {
                log.error(
                        "{} {} evento(s) de entrada esgotaram as tentativas: sao mensagens de clientes "
                                + "que nunca apareceram na conversa. Inspecione: SELECT id_externo, "
                                + "ultimo_erro FROM webhook_entrada WHERE esgotado_em IS NOT NULL;",
                        ProcessadorDeWebhookEntradaOperacoes.MARCADOR_ALARME,
                        esgotados);
            }
        });
    }
}
