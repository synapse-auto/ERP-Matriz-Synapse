package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executa a V56 sobre um banco parado na V55 e prova a nova faixa do CHECK (0–10).
 *
 * <p>Nao ha conversao de dado nesta etapa: producao ja arquivou e apagou as notas antigas fora do
 * Flyway. O teste afirma o CHECK novo — 0, 7 e 10 entram; 11 e recusado.
 */
@Testcontainers
class EscalaAvaliacaoMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    private String banco;
    private String url;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoNaVersaoAnterior() throws Exception {
        banco = "escala_aval_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        url = url(banco);
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(MigrationVersion.fromVersion("55")).migrate();
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V56: CHECK antigo era avaliacao_nota_check; 0/7/10 entram; 11 e recusado; sem RLS")
    void migration_novaFaixaAceita0e7e10ERecusa11() {
        String nomeCheckAntes = jdbc.queryForObject(
                """
                SELECT c.conname
                  FROM pg_constraint c
                 WHERE c.conrelid = 'avaliacao'::regclass
                   AND c.contype = 'c'
                """,
                String.class);
        assertThat(nomeCheckAntes)
                .as("nome gerado pelo Postgres para o CHECK inline da V2")
                .isEqualTo("avaliacao_nota_check");

        Boolean temRls = jdbc.queryForObject(
                """
                SELECT relrowsecurity OR relforcerowsecurity
                  FROM pg_class
                 WHERE oid = 'avaliacao'::regclass
                """,
                Boolean.class);
        assertThat(temRls).as("avaliacao nao tem RLS").isFalse();

        flyway(null).migrate();

        String nomeCheckDepois = jdbc.queryForObject(
                """
                SELECT c.conname
                  FROM pg_constraint c
                 WHERE c.conrelid = 'avaliacao'::regclass
                   AND c.contype = 'c'
                """,
                String.class);
        assertThat(nomeCheckDepois).isEqualTo("avaliacao_nota_entre_0_e_10");

        UUID usuario = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel)"
                        + " VALUES (?, 'Ana', ?, 'hash', 'ATENDENTE')",
                usuario,
                usuario + "@teste.local");
        jdbc.update("INSERT INTO lead (id, nome) VALUES (?, 'Lead CSAT')", lead);

        inserirAvaliacao(lead, usuario, 0);
        inserirAvaliacao(lead, usuario, 7);
        inserirAvaliacao(lead, usuario, 10);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM avaliacao", Integer.class)).isEqualTo(3);

        assertThatThrownBy(() -> inserirAvaliacao(lead, usuario, 11))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("avaliacao_nota_entre_0_e_10");
    }

    private void inserirAvaliacao(UUID leadId, UUID atendenteId, int nota) {
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status)"
                        + " VALUES (?, ?, ?, 'FINALIZADO'::status_atendimento)",
                atendimento,
                leadId,
                atendenteId);
        jdbc.update(
                "INSERT INTO avaliacao (id, atendimento_id, atendente_id, nota)"
                        + " VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                atendimento,
                atendenteId,
                nota);
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
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private static String url(String banco) {
        return "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/"
                + banco;
    }
}
