package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

/**
 * E233: a V110 acrescenta {@code mensagem.envio_de_template} sobre a tabela particionada, com dados.
 *
 * <p>Prova três coisas: (1) é só metadado — nenhuma partição é reescrita (o {@code relfilenode} de cada
 * uma não muda), o que importa numa VPS saturada; (2) linhas antigas passam a valer {@code FALSE};
 * (3) a imagem anterior segue funcionando contra o esquema novo: o {@code INSERT} e o {@code SELECT}
 * dela não nomeiam a coluna.
 */
@Testcontainers
class EnvioDeTemplateMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private String banco;
    private String url;
    private JdbcTemplate jdbc;
    private UUID atendimento;

    @BeforeEach
    void criarBancoNaVersaoAnterior() throws Exception {
        banco = "e233_v110_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        url = url(banco);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(MigrationVersion.fromVersion("109")).migrate();

        UUID lead = UUID.randomUUID();
        atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, nome) VALUES (?, 'Lead E233')", lead);
        jdbc.update("INSERT INTO atendimento (id, lead_id) VALUES (?, ?)", atendimento, lead);
        inserirAntiga("texto antigo");
        inserirAntiga("[template reativacao] Cliente");
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V110 é só metadado: nenhuma partição de mensagem é reescrita")
    void migration_naoReescrevePaticoes() {
        Map<String, Long> antes = arquivosDasParticoes();
        assertThat(antes).as("a tabela é particionada e tem filhas").isNotEmpty();

        flyway(null).migrate();

        assertThat(arquivosDasParticoes()).isEqualTo(antes);
    }

    @Test
    @DisplayName("V110 cria a coluna NOT NULL DEFAULT FALSE e as linhas antigas valem FALSE")
    void migration_colunaAditivaComDefault() {
        flyway(null).migrate();

        Map<String, Object> coluna = jdbc.queryForMap(
                "SELECT data_type, is_nullable, column_default FROM information_schema.columns"
                        + " WHERE table_name = 'mensagem' AND column_name = 'envio_de_template'");
        assertThat(coluna.get("data_type")).isEqualTo("boolean");
        assertThat(coluna.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(coluna.get("column_default"))).isEqualTo("false");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE atendimento_id = ? AND envio_de_template", Long.class,
                        atendimento))
                .as("nenhuma linha antiga é marcada: sem backfill")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimento))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a imagem anterior continua escrevendo e lendo, sem citar a coluna nova")
    void imagemAnterior_continuaFuncionando() {
        flyway(null).migrate();

        // SQL_REGISTRAR e SQL_POR_ID da imagem anterior (MensagemRepositorioJdbc): nenhum cita a coluna.
        int gravadas = jdbc.update(
                """
                INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo,
                                      conteudo, midia_url, midia_metadados, opcoes, status_entrega, enviado_em)
                     VALUES (?, ?, 'ATENDENTE'::remetente_tipo, NULL, 'TEXTO'::tipo_mensagem, 'escrita pela imagem anterior',
                             NULL, NULL::jsonb, NULL::jsonb, 'PENDENTE'::status_entrega, ?)
                """,
                UUID.randomUUID(),
                atendimento,
                Timestamp.from(Instant.now()));
        List<String> lidas = jdbc.queryForList(
                "SELECT conteudo FROM mensagem WHERE atendimento_id = ? ORDER BY enviado_em", String.class,
                atendimento);

        assertThat(gravadas).isOne();
        assertThat(lidas).contains("escrita pela imagem anterior");
        assertThat(jdbc.queryForObject(
                        "SELECT envio_de_template FROM mensagem WHERE conteudo = 'escrita pela imagem anterior'",
                        Boolean.class))
                .as("a imagem anterior grava sem marcar")
                .isFalse();
    }

    @Test
    @DisplayName("a imagem nova grava e lê o marcador verdadeiro")
    void imagemNova_gravaMarcadorVerdadeiro() {
        flyway(null).migrate();

        jdbc.update(
                """
                INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega,
                                      enviado_em, envio_de_template)
                     VALUES (?, ?, 'ATENDENTE', 'TEXTO', 'Olá Maria', 'PENDENTE', ?, TRUE)
                """,
                UUID.randomUUID(),
                atendimento,
                Timestamp.from(Instant.now()));

        assertThat(jdbc.queryForObject(
                        "SELECT envio_de_template FROM mensagem WHERE conteudo = 'Olá Maria'", Boolean.class))
                .isTrue();
    }

    private void inserirAntiga(String conteudo) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, 'LEAD', 'TEXTO', ?, 'ENTREGUE', ?)",
                UUID.randomUUID(),
                atendimento,
                conteudo,
                Timestamp.from(Instant.now()));
    }

    /** Arquivo físico de cada partição (e do pai): muda se, e só se, a tabela for reescrita. */
    private Map<String, Long> arquivosDasParticoes() {
        return jdbc.query(
                        """
                        SELECT c.relname, c.relfilenode
                          FROM pg_class c
                         WHERE c.relname = 'mensagem_default'
                            OR c.oid IN (SELECT inhrelid FROM pg_inherits WHERE inhparent = 'mensagem'::regclass)
                        """,
                        (rs, linha) -> Map.entry(rs.getString("relname"), rs.getLong("relfilenode")))
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + banco;
    }
}
