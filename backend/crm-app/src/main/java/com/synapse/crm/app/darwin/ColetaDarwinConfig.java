package com.synapse.crm.app.darwin;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.synapse.crm.core.infrastructure.darwin.DarwinProperties;

@Configuration
class ColetaDarwinConfig {
    @Bean(name="coletaDarwinExecutor", destroyMethod="shutdown")
    ThreadPoolTaskExecutor executor(DarwinProperties darwin) {
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1,darwin.concorrencia()));
        executor.setMaxPoolSize(Math.max(1,darwin.concorrencia()));
        executor.setQueueCapacity(0); // Rejeição imediata: nova atividade pode tentar de novo, sem fila ilimitada.
        executor.setThreadNamePrefix("darwin-coleta-");
        executor.initialize();
        return executor;
    }
}
