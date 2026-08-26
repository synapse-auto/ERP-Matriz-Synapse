package com.synapse.crm.atendimento.infrastructure.outbox;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bulkhead das chamadas de saida: o timeout de uma linha nao segura as demais. */
@Configuration
class PublicadorDaOutboxExecutorConfig {

    @Bean(name = "publicadorDaOutboxExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor publicadorDaOutboxExecutor(OutboxProperties propriedades) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(propriedades.concorrencia());
        executor.setMaxPoolSize(propriedades.concorrencia());
        executor.setQueueCapacity(Math.max(propriedades.lote(), propriedades.concorrencia()));
        executor.setThreadNamePrefix("outbox-envio-");
        executor.initialize();
        return executor;
    }
}
