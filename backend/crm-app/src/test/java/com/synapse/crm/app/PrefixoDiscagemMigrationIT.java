package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Prova a limpeza E180 em Postgres real, incluindo update, fusao segura e casos manuais. */
class PrefixoDiscagemMigrationIT extends PostgresIT {

    private String banco;
    private String url;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoNaVersaoAnterior() throws Exception {
        banco = "prefixo_discagem_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        url = url(banco);
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(MigrationVersion.fromVersion("71")).migrate();
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("normaliza leads, funde importado sem conversa e preserva ambiguos")
    void migration_corrigePrefixoComSeguranca() {
        UUID sobrevivente = criarLead("Paciente com conversa", "5548988593561");
        criarAtendimento(sobrevivente);
        UUID importado = criarLead("Nome da agenda", "01548988593561");
        UUID semPar = criarLead("Trunk sem par", "061999999999");
        UUID especial = criarLead("Servico 0800", "080012345678");
        UUID ambiguo = criarLead("Nao adivinhar", "015123456789012");

        flyway(null).migrate();

        assertThat(existe(importado)).as("o importado sem conversa e fundido").isFalse();
        assertThat(existe(sobrevivente)).isTrue();
        assertThat(telefone(sobrevivente)).isEqualTo("5548988593561");
        assertThat(telefone(semPar)).isEqualTo("5561999999999");
        assertThat(telefone(especial)).as("prefixo de servico nao e telefone geografico").isEqualTo("080012345678");
        assertThat(telefone(ambiguo)).as("entrada fora da regra fica para revisao").isEqualTo("015123456789012");

        // Uma segunda execucao nao encontra novamente o importado nem reescreve os canonicos.
        flyway(null).migrate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE telefone = ?", Integer.class, "5561999999999"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE telefone = ?", Integer.class, "080012345678"))
                .isEqualTo(1);
    }

    private UUID criarLead(String nome, String telefone) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')",
                id,
                nome,
                telefone);
        return id;
    }

    private void criarAtendimento(UUID leadId) {
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status) VALUES (?, ?, 'EM_IA')",
                UUID.randomUUID(),
                leadId);
    }

    private boolean existe(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM lead WHERE id = ?", Integer.class, id) == 1;
    }

    private String telefone(UUID id) {
        return jdbc.queryForObject("SELECT telefone FROM lead WHERE id = ?", String.class, id);
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
