package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executa a V41 sobre um banco parado na V40 e prova o backfill do responsavel atual. */
@Testcontainers
class LeituraPorUsuarioMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    private String banco;
    private String url;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoNaVersaoAnterior() throws Exception {
        banco = "leitura_v41_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        url = url(banco);
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(MigrationVersion.fromVersion("40")).migrate();
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V41 transforma o lido_ate legado na leitura do responsavel")
    void migration_backfillPreservaLeituraDoResponsavel() {
        UUID usuario = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        UUID atendimento = UUID.randomUUID();
        OffsetDateTime lidoAte = OffsetDateTime.parse("2026-08-25T12:00:00Z");

        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel)"
                        + " VALUES (?, 'Ana', ?, 'hash', 'ATENDENTE')",
                usuario,
                usuario + "@teste.local");
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id) VALUES (?, 'Lead', ?)",
                lead,
                usuario);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, lido_ate)"
                        + " VALUES (?, ?, ?, ?)",
                atendimento,
                lead,
                usuario,
                lidoAte);

        flyway(null).migrate();

        assertThat(jdbc.queryForObject(
                        "SELECT lido_ate FROM atendimento_leitura"
                                + " WHERE atendimento_id = ? AND usuario_id = ?",
                        OffsetDateTime.class,
                        atendimento,
                        usuario))
                .isEqualTo(lidoAte);
    }

    @Test
    @DisplayName("RLS deixa cada usuario ler somente sua propria linha")
    void rls_leituraEPorUsuario() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        UUID atendimento = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        criarUsuario(ana, "ana");
        criarUsuario(bruno, "bruno");
        jdbc.update("INSERT INTO lead (id, nome) VALUES (?, 'Lead RLS')", lead);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, lido_ate) VALUES (?, ?, now())",
                atendimento,
                lead);
        flyway(null).migrate();
        jdbc.update(
                "INSERT INTO atendimento_leitura (atendimento_id, usuario_id, lido_ate)"
                        + " VALUES (?, ?, now()), (?, ?, now())",
                atendimento,
                ana,
                atendimento,
                bruno);

        try (Connection conexao = DriverManager.getConnection(
                        url, POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            conexao.setAutoCommit(false);
            comando.execute("SET LOCAL ROLE synapse_app");
            comando.execute("SET LOCAL app.usuario_id = '" + ana + "'");
            try (PreparedStatement consulta = conexao.prepareStatement(
                            "SELECT count(*) FROM atendimento_leitura")) {
                try (var resultado = consulta.executeQuery()) {
                    assertThat(resultado.next()).isTrue();
                    assertThat(resultado.getLong(1)).isEqualTo(1);
                }
            }
            conexao.rollback();
        }
    }

    private void criarUsuario(UUID id, String nome) {
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel)"
                        + " VALUES (?, ?, ?, 'hash', 'ATENDENTE')",
                id,
                nome,
                id + "@teste.local");
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
