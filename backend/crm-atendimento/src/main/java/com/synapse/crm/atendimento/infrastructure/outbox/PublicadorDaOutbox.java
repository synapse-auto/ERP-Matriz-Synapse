package com.synapse.crm.atendimento.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Agendamento do drenador da outbox. So dispara o job e declara o {@link ContextoDeServico}; a
 * logica transacional mora em {@link PublicadorDaOutboxOperacoes}, injetada aqui.
 *
 * <p>As duas responsabilidades vivem em beans separados de proposito. Um {@code @Scheduled} que
 * chamasse {@code this::rodada} dentro da propria classe pularia o proxy CGLIB do Spring — a chamada
 * seria uma invocacao Java comum, sem interceptador nenhum, e {@code @Transactional} nao teria
 * efeito. Foi exatamente esse bug (E07b): a outbox nunca drenava porque {@code rodada()} lancava
 * {@code AcessoSemTransacaoException} a cada tick, e o {@code @Scheduled} engolia a excecao e so
 * logava — sem nenhum teste chamando este metodo pelo bean do Spring, ninguem percebeu. Chamando
 * {@link PublicadorDaOutboxOperacoes#rodada()} atraves da instancia injetada, a chamada sai por fora
 * e passa pelo proxy de verdade.
 */
@Component
public class PublicadorDaOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDaOutbox.class);

    private final PublicadorDaOutboxOperacoes operacoes;

    public PublicadorDaOutbox(PublicadorDaOutboxOperacoes operacoes) {
        this.operacoes = operacoes;
    }

    /** Agendamento curto: e o que define a latencia percebida entre o clique e a saida. */
    @Scheduled(fixedDelayString = "${synapse.canal.outbox.intervalo-ms:1000}")
    public void publicarPendentes() {
        ContextoDeServico.executarComo("publicador-outbox", operacoes::rodada);
    }

    /**
     * Alarme periodico. Rede de seguranca sem alarme some do radar ate a divida ficar cara — a mesma
     * licao da particao DEFAULT da V5.
     */
    @Scheduled(cron = "${synapse.canal.outbox.cron-alerta:0 */15 * * * *}")
    public void alertarSobreEsgotadas() {
        ContextoDeServico.executarComo("alerta-outbox", () -> {
            long esgotadas = operacoes.contarEsgotadas();
            if (esgotadas > 0) {
                log.error(
                        "{} {} mensagem(ns) na outbox esgotaram as tentativas e nunca chegaram ao "
                                + "cliente. Inspecione: SELECT id, ultimo_erro, tentativas FROM "
                                + "outbox_evento WHERE esgotado_em IS NOT NULL;",
                        PublicadorDaOutboxOperacoes.MARCADOR_ALARME,
                        esgotadas);
            }
        });
    }
}
