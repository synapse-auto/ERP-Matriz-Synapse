package com.synapse.crm.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Liga o agendamento por {@code @Scheduled}.
 *
 * <p>Mora na aplicacao, e nao no modulo que agenda, porque ligar o agendador e decisao de
 * composicao: um modulo importado como biblioteca nao deveria decidir que a aplicacao inteira passa
 * a ter um scheduler.
 *
 * <p>O primeiro job a depender disso e a manutencao das particoes de {@code mensagem}.
 *
 * <p>Registra um {@link org.springframework.util.ErrorHandler} proprio no {@code TaskScheduler}
 * porque o padrao do Spring ({@code LoggingErrorHandler}) loga a excecao e segue, sem marcador — o
 * mesmo formato de qualquer log de rotina. Foi exatamente isso que escondeu o bug da E07b: o
 * agendador de {@code @Scheduled} engolia {@code AcessoSemTransacaoException} a cada tick, e o log
 * virou ruido de fundo em vez de alarme. Este handler garante que qualquer excecao que escape de um
 * job agendado — esta ou a proxima — sai com o mesmo marcador greppavel dos outros alarmes do
 * projeto, em vez de morrer em silencio.
 */
@Configuration
@EnableScheduling
public class AgendamentoConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoConfig.class);

    /** Marcador do alarme de job agendado que lancou. Fixo e greppavel, como os demais. */
    public static final String MARCADOR_ALARME = "[ALERTA_JOB_AGENDADO_FALHOU]";

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()));
        scheduler.setThreadNamePrefix("agendado-");
        scheduler.setErrorHandler(throwable -> log.error(
                "{} uma execucao agendada lancou e a rodada foi perdida. Sem este alarme o erro "
                        + "ficaria so no log de rotina, indistinguivel de qualquer outra linha. Causa: {}",
                MARCADOR_ALARME,
                throwable.toString(),
                throwable));
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
