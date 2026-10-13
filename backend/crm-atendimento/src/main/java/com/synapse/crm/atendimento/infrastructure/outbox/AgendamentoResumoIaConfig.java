package com.synapse.crm.atendimento.infrastructure.outbox;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Uma rodada serial em executor exclusivo: HTTP lento não ocupa os timers de mensagens. */
@Configuration
class AgendamentoResumoIaConfig {
    @Bean(name = "agendamentoResumoIa", destroyMethod = "shutdown")
    ThreadPoolTaskScheduler agendamentoResumoIa() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("resumo-ia-");
        scheduler.initialize();
        return scheduler;
    }
}
