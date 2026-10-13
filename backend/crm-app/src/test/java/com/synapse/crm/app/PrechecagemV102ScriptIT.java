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
 * A pre-checagem da V102 (docker/operacoes/e231-v102-prechecagem.sql) e entregue para o operador rodar nos bancos de
 * producao, somente leitura. Este teste prova que ela executa contra o schema real e que ela distingue os tres
 * estados: nenhuma violacao (LIBERAR), linha que violaria (BLOQUEAR) e CHECK atual nao validada (ATENCAO). As linhas que
 * violam so podem existir numa copia sem constraints: no schema real as CHECK da V97 as impedem.
 */
@Testcontainers
class PrechecagemV102ScriptIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withFileSystemBind(arquivo("e231-v102-prechecagem.sql"), "/tmp/prechecagem.sql", BindMode.READ_ONLY);

    private String banco;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoMigrado() throws Exception {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        banco = "precheck_v102_" + UUID.randomUUID().toString().replace("-", "");
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
    @DisplayName("schema real: roda, nao escreve nada e libera (as CHECK da V97 estao validadas e nao ha violacao)")
    void schemaRealLibera() throws Exception {
        UUID lead = jdbc.queryForObject("INSERT INTO lead (nome) VALUES ('Lead') RETURNING id", UUID.class);
        UUID usuario = usuario();
        jdbc.update(
                "INSERT INTO atendimento (lead_id, status, resultado_venda, venda_registrada_por_id, venda_registrada_em,"
                        + " origem_resultado_venda) VALUES (?, 'EM_ATENDIMENTO'::status_atendimento, 'VENDEU', ?, now(), 'MANUAL')",
                lead, usuario);

        Container.ExecResult resultado = psql(null);

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        assertThat(veredito(resultado)).isEqualTo("LIBERAR");
        assertThat(resultado.getStdout()).contains("ck_atendimento_estado_resultado_venda");
    }

    @Test
    @DisplayName("copia sem constraints com linha que violaria a V102: BLOQUEAR e conta as violacoes")
    void linhaQueViolaBloqueia() throws Exception {
        copiaSemConstraints();
        // origem fora do dominio e estado incoerente (resultado sem quem registrou, origem MANUAL)
        jdbc.update("INSERT INTO scratch.atendimento (lead_id, status, origem_resultado_venda) VALUES (?, 'EM_ATENDIMENTO'::status_atendimento, 'OUTRA')", lead());
        jdbc.update(
                "INSERT INTO scratch.atendimento (lead_id, status, resultado_venda, venda_registrada_em, origem_resultado_venda)"
                        + " VALUES (?, 'EM_ATENDIMENTO'::status_atendimento, 'VENDEU', now(), 'MANUAL')",
                lead());

        Container.ExecResult resultado = psql("scratch");

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        assertThat(veredito(resultado)).startsWith("BLOQUEAR");
        // 1 linha viola a CHECK de origem; as 2 violam a de estado (origem sem resultado; resultado sem quem registrou)
        assertThat(resultado.getStdout()).containsPattern("(?m)^\\s*1 \\|\\s+2 \\|\\s+2$");
    }

    @Test
    @DisplayName("copia sem constraints e sem violacao (inclui AUTOMACAO sem quem registrou): ATENCAO, nao LIBERAR")
    void semConstraintValidadaAvisa() throws Exception {
        copiaSemConstraints();
        jdbc.update(
                "INSERT INTO scratch.atendimento (lead_id, status, resultado_venda, venda_registrada_em, origem_resultado_venda)"
                        + " VALUES (?, 'EM_ATENDIMENTO'::status_atendimento, 'VENDEU', now(), 'AUTOMACAO')",
                lead());

        Container.ExecResult resultado = psql("scratch");

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        assertThat(veredito(resultado)).startsWith("ATENCAO");
        assertThat(resultado.getStdout()).containsPattern("(?m)linhas_origem_automacao\\s*\\R-+\\R\\s*1$");
    }

    @Test
    @DisplayName("e somente leitura: uma escrita dentro do bloco seria recusada pelo Postgres")
    void bancoNaoMudaDepoisDoScript() throws Exception {
        UUID lead = jdbc.queryForObject("INSERT INTO lead (nome) VALUES ('Lead') RETURNING id", UUID.class);
        jdbc.update("INSERT INTO atendimento (lead_id, status) VALUES (?, 'EM_IA'::status_atendimento)", lead);
        long antes = jdbc.queryForObject("SELECT count(*) FROM atendimento", Long.class);

        assertThat(psql(null).getExitCode()).isZero();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atendimento", Long.class)).isEqualTo(antes);
        String script = Files.readString(Path.of(arquivo("e231-v102-prechecagem.sql")));
        assertThat(script).contains("BEGIN READ ONLY;").doesNotContainPattern("(?im)^\\s*(INSERT|UPDATE|DELETE|ALTER|DROP|CREATE)\\b");
    }

    /** A linha do resultado depois do cabecalho "veredito" (o texto do cabecalho do script tambem cita as palavras). */
    private static String veredito(Container.ExecResult resultado) {
        String[] linhas = resultado.getStdout().split("\\R");
        for (int indice = 0; indice < linhas.length; indice++) {
            if (linhas[indice].trim().equals("veredito")) {
                return linhas[indice + 2].trim();
            }
        }
        throw new AssertionError("script sem veredito: " + resultado.getStdout());
    }

    private UUID lead() {
        return jdbc.queryForObject("INSERT INTO lead (nome, telefone) VALUES ('L', ?) RETURNING id", UUID.class,
                "5561" + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L));
    }

    private UUID usuario() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo) VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE)",
                id, "Atendente", id + "@e231.invalid", "x");
        return id;
    }

    private void copiaSemConstraints() {
        jdbc.execute("CREATE SCHEMA scratch");
        jdbc.execute("CREATE TABLE scratch.atendimento (LIKE public.atendimento INCLUDING DEFAULTS)");
    }

    private Container.ExecResult psql(String schemaDeBusca) throws Exception {
        if (schemaDeBusca == null) {
            return POSTGRES.execInContainer("psql", "-X", "-U", POSTGRES.getUsername(), "-d", banco, "-f", "/tmp/prechecagem.sql");
        }
        return POSTGRES.execInContainer(
                "psql", "-X", "-U", POSTGRES.getUsername(), "-d", banco,
                "-c", "SET search_path = " + schemaDeBusca + ", public",
                "-f", "/tmp/prechecagem.sql");
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
