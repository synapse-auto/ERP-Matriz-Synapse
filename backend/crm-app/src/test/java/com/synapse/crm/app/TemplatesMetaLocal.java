package com.synapse.crm.app;

import org.springframework.boot.SpringApplication;

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
        SpringApplication.run(SynapseCrmApplication.class,
                "--spring.profiles.active=dev", "--server.address=127.0.0.1", "--server.port=8093");
    }
}
