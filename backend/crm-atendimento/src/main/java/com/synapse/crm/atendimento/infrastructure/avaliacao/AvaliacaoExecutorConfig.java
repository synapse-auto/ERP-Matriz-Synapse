package com.synapse.crm.atendimento.infrastructure.avaliacao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class AvaliacaoExecutorConfig {
    @Bean(name = "avaliacaoExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor avaliacaoExecutor(AvaliacaoWebhookProperties config) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.concorrencia());
        executor.setMaxPoolSize(config.concorrencia());
        executor.setQueueCapacity(config.fila());
        executor.setThreadNamePrefix("avaliacao-n8n-");
        executor.initialize();
        return executor;
    }
}
