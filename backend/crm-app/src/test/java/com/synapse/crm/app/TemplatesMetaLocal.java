package com.synapse.crm.app;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.context.event.ContextClosedEvent;

import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

/** Aplicacao real opt-in com banco descartavel e adaptador Meta restrito ao stub local. */
public final class TemplatesMetaLocal {
    private TemplatesMetaLocal() {}

    public static void main(String[] args) {
        if (!"true".equals(System.getenv("TEMPLATES_VISUAL_LOCAL"))) {
            throw new IllegalStateException("fixture exclusiva para validacao local opt-in");
        }
        // A base de IT prepara Postgres/Redis descartaveis e aplica migrations + seed sintetica.
        for (String pool : new String[] {"general", "chat"}) {
            String prefixo = "synapse.datasource." + pool;
            System.setProperty(prefixo + ".url", PostgresIT.POSTGRES.getJdbcUrl());
            System.setProperty(prefixo + ".username", PostgresIT.POSTGRES.getUsername());
            System.setProperty(prefixo + ".password", PostgresIT.POSTGRES.getPassword());
            System.setProperty(prefixo + ".hikari.maximum-pool-size", "2");
        }
        System.setProperty("spring.data.redis.host", PostgresIT.REDIS.getHost());
        System.setProperty("spring.data.redis.port", PostgresIT.REDIS.getMappedPort(6379).toString());
        System.setProperty("synapse.seguranca.jwt-segredo", PostgresIT.SEGREDO_JWT_DE_TESTE);
        System.setProperty("synapse.canal.whatsapp.provedor", "meta-cloud");
        System.setProperty("synapse.canal.whatsapp.url-base", "http://127.0.0.1:8089/v21.0");
        System.setProperty("synapse.canal.whatsapp.token", "token-stub-e2e");
        System.setProperty("synapse.canal.whatsapp.conta-negocio", "waba-e2e");
        System.setProperty("synapse.canal.whatsapp.numero-principal", "phone-e2e");
        System.setProperty("synapse.agendamento.habilitado", "false");
        System.setProperty("synapse.saude.critica.monitoramento-habilitado", "false");
        System.setProperty("synapse.seguranca.frontend-origem", "http://localhost:3013");
        System.setProperty("synapse.tempo-real.origens-permitidas", "http://localhost:3013");
        var contexto = SpringApplication.run(SynapseCrmApplication.class,
                "--spring.profiles.active=dev", "--server.address=127.0.0.1", "--server.port=8093");
        // Somente a outbox deste banco descartavel: os demais jobs continuam desligados.
        // Exercita o mesmo ponto de entrada do runtime, inclusive proxy e contexto de servico.
        var publicador = contexto.getBean(PublicadorDaOutbox.class);
        var executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("templates-local-outbox").factory());
        long intervalo = contexto.getEnvironment().getRequiredProperty("synapse.canal.outbox.intervalo-ms", Long.class);
        executor.scheduleWithFixedDelay(publicador::publicarPendentes, 0, intervalo, TimeUnit.MILLISECONDS);
        contexto.addApplicationListener(evento -> {
            if (evento instanceof ContextClosedEvent) executor.shutdownNow();
        });
    }
}
