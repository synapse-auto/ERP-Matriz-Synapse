package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executa a V26 sobre um banco parado na V25, inclusive no caminho que precisa abortar. */
@Testcontainers
class TelefoneComDdiMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    private String banco;
    private String url;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoNaVersaoAnterior() throws Exception {
        banco = "telefone_v26_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        url = url(banco);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(MigrationVersion.fromVersion("25")).migrate();
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V26 completa DDI e preserva o indice unico")
    void migration_semColisao_completaEContinuaUnica() {
        UUID local = UUID.randomUUID();
        UUID internacional = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')",
                local,
                "Cliente local",
                "61999999999");
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')",
                internacional,
                "Cliente internacional",
                "351219999999");

        flyway(null).migrate();

        assertThat(jdbc.queryForObject(
                        "SELECT telefone FROM lead WHERE id = ?", String.class, local))
                .isEqualTo("5561999999999");
        assertThat(jdbc.queryForObject(
                        "SELECT telefone FROM lead WHERE id = ?", String.class, internacional))
                .isEqualTo("351219999999");
        assertThat(jdbc.queryForObject(
                        "SELECT indisvalid FROM pg_index WHERE indexrelid = 'ux_lead_telefone'::regclass",
                        Boolean.class))
                .isTrue();
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')",
                        UUID.randomUUID(),
                        "Duplicado",
                        "5561999999999"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("V26 interrompe antes de alterar e lista os dois leads que colidiriam")
    void migration_comColisao_listaParesEAborta() {
        UUID local = UUID.randomUUID();
        UUID internacional = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')",
                local,
                "Cliente local",
                "61999999999");
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')",
                internacional,
                "Cliente Meta",
                "5561999999999");

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Telefones duplicados apos completar o DDI")
                .hasStackTraceContaining(local + " | Cliente local | 61999999999")
                .hasStackTraceContaining(internacional + " | Cliente Meta | 5561999999999");

        assertThat(jdbc.queryForObject(
                        "SELECT telefone FROM lead WHERE id = ?", String.class, local))
                .isEqualTo("61999999999");
    }

    private Flyway flyway(MigrationVersion alvo) {
        var configuracao = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("telefone_ddi_padrao", "55"));
        if (alvo != null) {
            configuracao.target(alvo);
        }
        return configuracao.load();
    }

    private void executarNoBancoAdministrativo(String sql) throws Exception {
        try (Connection conexao = DriverManager.getConnection(
                        url("postgres"), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private static String url(String banco) {
        return "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(5432)
                + "/"
                + banco;
    }
}
