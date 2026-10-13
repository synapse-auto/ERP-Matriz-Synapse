package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Os scripts de EXPLAIN do E229 (docker/operacoes/e229-*) sao entregues para o operador rodar na VPS; este teste prova
 * que eles executam de ponta a ponta contra o schema real (sintaxe, papel synapse_app, variaveis) e medem tudo o que
 * dizem medir. NAO mede desempenho: o banco de teste esta vazio.
 */
@Testcontainers
class ExplainDoRodizioScriptsIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withFileSystemBind(arquivo("e229-explain-rodizio-antes.sql"), "/tmp/antes.sql", BindMode.READ_ONLY)
            .withFileSystemBind(arquivo("e229-explain-rodizio-depois.sql"), "/tmp/depois.sql", BindMode.READ_ONLY);

    private String banco;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoMigrado() throws Exception {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        banco = "explain_e229_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        String url = url(banco);
        Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("telefone_ddi_padrao", "55"))
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("antes: roda sem variaveis e mede as tres consultas sem parametro")
    void antesExecuta() throws Exception {
        Container.ExecResult resultado = psql("/tmp/antes.sql");

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        assertThat(contar(resultado.getStdout(), "Execution Time")).isEqualTo(9);
    }

    @Test
    @DisplayName("depois: roda com setor, usuario e atendimento e mede as seis consultas (literal e generico)")
    void depoisExecuta() throws Exception {
        UUID setor = jdbc.queryForObject("INSERT INTO setor (nome) VALUES ('Vendas') RETURNING id", UUID.class);
        UUID usuario = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo) VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE)",
                usuario, "Atendente", usuario + "@e229.invalid", "x");
        UUID lead = jdbc.queryForObject("INSERT INTO lead (nome) VALUES ('Lead') RETURNING id", UUID.class);
        UUID atendimento = jdbc.queryForObject(
                "INSERT INTO atendimento (lead_id, status) VALUES (?, 'EM_IA'::status_atendimento) RETURNING id",
                UUID.class, lead);

        Container.ExecResult resultado = psql(
                "/tmp/depois.sql", "-v", "setor=" + setor, "-v", "usuario=" + usuario, "-v", "atendimento=" + atendimento);

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        assertThat(contar(resultado.getStdout(), "Execution Time")).isEqualTo(30);
    }

    @Test
    @DisplayName("depois: sem as variaveis exigidas o script para em vez de medir errado")
    void depoisSemVariaveisPara() throws Exception {
        Container.ExecResult resultado = psql("/tmp/depois.sql");

        assertThat(resultado.getExitCode()).isNotZero();
    }

    private Container.ExecResult psql(String script, String... variaveis) throws Exception {
        String[] base = {"psql", "-X", "-q", "-U", POSTGRES.getUsername(), "-d", banco};
        String[] todos = new String[base.length + variaveis.length + 2];
        System.arraycopy(base, 0, todos, 0, base.length);
        System.arraycopy(variaveis, 0, todos, base.length, variaveis.length);
        todos[todos.length - 2] = "-f";
        todos[todos.length - 1] = script;
        return POSTGRES.execInContainer(todos);
    }

    private static int contar(String texto, String trecho) {
        return (int) texto.lines().filter(linha -> linha.contains(trecho)).count();
    }

    private void executarNoBancoAdministrativo(String sql) throws Exception {
        try (Connection conexao =
                        DriverManager.getConnection(url("postgres"), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private static String arquivo(String nome) {
        Path atual = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (atual != null) {
            Path candidato = atual.resolve("docker/operacoes/" + nome);
            if (Files.isRegularFile(candidato)) {
                return candidato.toString();
            }
            atual = atual.getParent();
        }
        throw new IllegalStateException("Arquivo nao encontrado: docker/operacoes/" + nome);
    }

    private static String url(String banco) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + banco;
    }
}
