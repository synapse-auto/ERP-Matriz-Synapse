package com.synapse.crm.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/** Aplicação REAL para a validação headed, com banco descartável e provedor fake de testes. */
public final class ConviteColaborativoLocal {
    private ConviteColaborativoLocal() {}

    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("CONVITE_VISUAL_LOCAL"))) {
            throw new IllegalStateException("execução exclusiva da fixture local opt-in");
        }
        var postgres = new PostgreSQLContainer<>("postgres:15-alpine").withDatabaseName("convite_auditoria");
        var redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        postgres.start();
        redis.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .placeholders(Map.of("telefone_ddi_padrao", "55")).load().migrate();
        for (String pool : new String[] {"general", "chat"}) {
            System.setProperty("synapse.datasource." + pool + ".url", postgres.getJdbcUrl());
            System.setProperty("synapse.datasource." + pool + ".username", postgres.getUsername());
            System.setProperty("synapse.datasource." + pool + ".password", postgres.getPassword());
            System.setProperty("synapse.datasource." + pool + ".hikari.maximum-pool-size", "2");
        }
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", redis.getMappedPort(6379).toString());
        System.setProperty("synapse.seguranca.jwt-segredo", PostgresIT.SEGREDO_JWT_DE_TESTE);
        System.setProperty("synapse.canal.whatsapp.provedor", "fake");
        System.setProperty("synapse.agendamento.habilitado", "false");
        System.setProperty("synapse.saude.critica.monitoramento-habilitado", "false");
        System.setProperty("synapse.tempo-real.origens-permitidas", "http://localhost:3011");
        System.setProperty("synapse.seguranca.frontend-origem", "http://localhost:3011");
        var contexto = SpringApplication.run(SynapseCrmApplication.class,
                "--spring.profiles.active=dev", "--server.address=127.0.0.1", "--server.port=8091");
        contexto.getBean(JdbcTemplate.class).execute(Files.readString(
                Path.of("..", "frontend", "e2e", "fixtures", "convite-colaborativo.sql")));
        Path saida = Path.of("..", "output", "convite-local-runtime.json");
        Files.createDirectories(saida.getParent());
        Files.writeString(saida, "{\"backend\":\"http://localhost:8091\",\"containerPostgres\":\""
                + postgres.getContainerId() + "\",\"usuarioPostgres\":\"" + postgres.getUsername() + "\"}");
    }
}
