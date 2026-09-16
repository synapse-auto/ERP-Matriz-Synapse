package com.synapse.crm.app.foto;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bulkhead da consulta de foto: nenhum timeout de provedor ocupa o pool do chat. */
@Configuration
class FotoDePerfilExecutorConfig {

    @Bean(name = "fotoDePerfilExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor fotoDePerfilExecutor(FotoDePerfilProperties propriedades) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(propriedades.concorrencia());
        executor.setMaxPoolSize(propriedades.concorrencia());
        executor.setQueueCapacity(propriedades.fila());
        executor.setThreadNamePrefix("foto-lead-");
        executor.initialize();
        return executor;
    }
}
