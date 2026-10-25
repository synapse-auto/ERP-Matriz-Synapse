package com.synapse.crm.app.provisionamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container.ExecResult;

import com.synapse.crm.app.PostgresIT;

/** Exercita o psql real e os arquivos operacionais, sem substituir o cadastro por SQL do teste. */
class CadastroCanalZApiIT extends PostgresIT {
    private static final String INSTANCIA = "INSTANCIA_SINTETICA_Z_API";
    private static final String TELEFONE = "5511999990000";
    private String banco;
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() throws Exception {
        banco = "cadastro_zapi_" + UUID.randomUUID().toString().replace("-", "");
        administrativo("CREATE DATABASE " + banco);
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + banco;
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("telefone_ddi_padrao", "55"))
                .load().migrate();
        for (String arquivo : List.of("cadastrar-canal-z-api.sql", "ativar-canal-z-api-homologacao.sql")) {
            // Somente fixture SQL: evita o empacotador TAR incompatível da base sem alterar
            // dependencias de producao. O script e passado como argumento, nunca interpolado.
            String conteudo = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("../../docker/provisionamento", arquivo)));
            var copia = POSTGRES.execInContainer("sh", "-c", "printf '%s' \"$1\" | base64 -d > \"$2\"", "sh", conteudo, "/tmp/" + arquivo);
            assertThat(copia.getExitCode()).as(copia.getStderr()).isZero();
            assertThat(POSTGRES.execInContainer("test", "-s", "/tmp/" + arquivo).getExitCode()).isZero();
        }
    }

    @AfterEach
    void limpar() throws Exception {
        administrativo("DROP DATABASE " + banco + " WITH (FORCE)");
    }

    @Test
    void simulacaoNaoPersisteNemRetornaUuidUtilizavel() throws Exception {
        ExecResult resultado = cadastrar("SIMULAR");
        assertThat(resultado.getExitCode()).isZero();
        assertThat(resultado.getStdout()).contains("nao foram persistidos").doesNotContain(TELEFONE, INSTANCIA);
        assertThat(contar("canal")).isZero();
        assertThat(contar("audit_log")).isZero();
    }

    @Test
    void cadastroGeraUuidInativoReferenciaSemTokenEAuditoriaEReexecucaoNaoDuplica() throws Exception {
        var resultado = cadastrar("CADASTRAR");
        assertThat(resultado.getExitCode()).as(resultado.getStderr()).isZero();
        UUID id = credencial();
        assertThat(id).isNotNull();
        assertThat(jdbc.queryForMap("SELECT ativo, token_ref, numero FROM canal_credencial WHERE id = ?", id))
                .containsEntry("ativo", false).containsEntry("token_ref", "env://Z_API_TOKEN")
                .containsEntry("numero", TELEFONE);
        assertThat(jdbc.queryForObject("SELECT ativo FROM canal", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT dados_depois::text FROM audit_log", String.class))
                .contains("executorSql").doesNotContain(TELEFONE, INSTANCIA);
        assertThat(cadastrar("CADASTRAR").getExitCode()).isZero();
        assertThat(credencial()).isEqualTo(id);
        assertThat(contar("canal_credencial")).isEqualTo(1);
        assertThat(contar("audit_log")).isEqualTo(1);
    }

    @Test
    void cadastroPreservaTodosOsCamposDaCredencialUzapiESeuCanal() throws Exception {
        UUID canal = jdbc.queryForObject("INSERT INTO canal(nome,tipo) VALUES ('Anterior','WHATSAPP') RETURNING id", UUID.class);
        jdbc.update("INSERT INTO canal_credencial(canal_id,numero,identificador_externo,token_ref) VALUES (?, '5511999991111', 'UZAPI_ANTIGA', 'env://UZAPI_TOKEN')", canal);
        String antes = jdbc.queryForObject("SELECT row_to_json(c)::text FROM canal_credencial c", String.class);
        assertThat(cadastrar("CADASTRAR").getExitCode()).isZero();
        assertThat(jdbc.queryForObject("SELECT row_to_json(c)::text FROM canal_credencial c WHERE canal_id = ?", String.class, canal)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT ativo FROM canal WHERE id = ?", Boolean.class, canal)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"banco_esperado=outro_banco", "instancia_id=", "instancia_id=../segredo", "telefone=nao-numero", "telefone=+5511999990000", "canal_nome=", "confirmar=ATIVAR"})
    void parametrosInvalidosNaoPersistem(String parametro) throws Exception {
        ExecResult resultado = executar("cadastrar-canal-z-api.sql", "confirmar=CADASTRAR", parametro);
        // O override e o ultimo parametro, inclusive para confirmar.
        assertThat(resultado.getExitCode()).isNotZero();
        assertThat(contar("canal")).isZero();
        assertThat(resultado.getStderr()).doesNotContain(TELEFONE);
    }

    @Test
    void identidadeDeOutroProvedorNaoPodeSerAdotadaMesmoPorMesmoNome() throws Exception {
        UUID canal = jdbc.queryForObject("INSERT INTO canal(nome,tipo) VALUES ('Z-API HML','WHATSAPP') RETURNING id", UUID.class);
        jdbc.update("INSERT INTO canal_credencial(canal_id,numero,identificador_externo,token_ref) VALUES (?, ?, ?, 'env://UZAPI_TOKEN')", canal, TELEFONE, INSTANCIA);
        assertThat(cadastrar("CADASTRAR").getExitCode()).isNotZero();
        assertThat(contar("canal_credencial")).isEqualTo(1);
        assertThat(contar("audit_log")).isZero();
    }

    @Test
    void identidadeUsadaSobOutroNomeNaoPodeSerDuplicada() throws Exception {
        assertThat(cadastrar("CADASTRAR").getExitCode()).isZero();
        assertThat(executar("cadastrar-canal-z-api.sql", "canal_nome=Outro canal", "confirmar=CADASTRAR").getExitCode()).isNotZero();
        assertThat(contar("canal")).isEqualTo(1);
    }

    @Test
    void telefoneDivergenteNaoReescreveCadastro() throws Exception {
        assertThat(cadastrar("CADASTRAR").getExitCode()).isZero();
        assertThat(executar("cadastrar-canal-z-api.sql", "telefone=5511999992222", "confirmar=CADASTRAR").getExitCode()).isNotZero();
        assertThat(jdbc.queryForObject("SELECT numero FROM canal_credencial", String.class)).isEqualTo(TELEFONE);
    }

    @Test
    void nomeComAspasNaoExecutaSql() throws Exception {
        String nome = "Canal '); DROP TABLE canal; --";
        assertThat(executar("cadastrar-canal-z-api.sql", "canal_nome=" + nome, "confirmar=CADASTRAR").getExitCode()).isZero();
        assertThat(jdbc.queryForObject("SELECT nome FROM canal", String.class)).isEqualTo(nome);
    }

    @Test
    void inventarioRealNaoExportaSegredosOuConteudo() throws Exception {
        jdbc.update("INSERT INTO outbox_evento(tipo,payload) VALUES ('canal.mensagem.enviar', '{\"provedorOrigem\":\"uzapi-autotic\",\"conteudo\":\"NAO_EXPORTAR_CONTEUDO\",\"token\":\"NAO_EXPORTAR_SEGREDO\"}')");
        String conteudo = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("../../docs/diagnosticos/inventario-corte-z-api.sql")));
        assertThat(POSTGRES.execInContainer("sh", "-c", "printf '%s' \"$1\" | base64 -d > \"$2\"", "sh", conteudo, "/tmp/inventario-z-api.sql").getExitCode()).isZero();
        var resultado = executar("inventario-z-api.sql");
        assertThat(resultado.getExitCode()).as(resultado.getStderr()).isZero();
        assertThat(resultado.getStdout()).contains("uzapi-autotic").doesNotContain("NAO_EXPORTAR", TELEFONE);
    }

    @Test
    void duasExecucoesConcorrentesGeramUmaCredencial() throws Exception {
        var primeira = CompletableFuture.supplyAsync(() -> cadastrarSemExcecao());
        var segunda = CompletableFuture.supplyAsync(() -> cadastrarSemExcecao());
        assertThat(primeira.get().getExitCode()).isZero();
        assertThat(segunda.get().getExitCode()).isZero();
        assertThat(contar("canal_credencial")).isEqualTo(1);
    }

    @Test
    void ativacaoHmlExplicitaPreservaCredencialAnteriorESoUmCanalFicaAtivo() throws Exception {
        UUID anterior = jdbc.queryForObject("INSERT INTO canal(nome,tipo) VALUES ('Anterior','WHATSAPP') RETURNING id", UUID.class);
        jdbc.update("INSERT INTO canal_credencial(canal_id,numero,identificador_externo,token_ref) VALUES (?, '5511999991111', 'ANTIGA', 'env://UZAPI_TOKEN')", anterior);
        String estado = jdbc.queryForObject("SELECT row_to_json(c)::text FROM canal_credencial c", String.class);
        assertThat(cadastrar("CADASTRAR").getExitCode()).isZero();
        assertThat(ativar("HOMOLOGACAO_ISOLADA").getExitCode()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM canal WHERE ativo", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ativo FROM canal_credencial WHERE id = ?", Boolean.class, credencial())).isTrue();
        assertThat(jdbc.queryForObject("SELECT row_to_json(c)::text FROM canal_credencial c WHERE canal_id = ?", String.class, anterior)).isEqualTo(estado);
    }

    @Test
    void ativacaoSemConfirmacaoOuComDadoOperacionalRecusaSemEfeito() throws Exception {
        assertThat(cadastrar("CADASTRAR").getExitCode()).isZero();
        assertThat(ativar("").getExitCode()).isNotZero();
        jdbc.update("INSERT INTO lead(nome,telefone) VALUES ('Cliente sintetico','5511999993333')");
        assertThat(ativar("HOMOLOGACAO_ISOLADA").getExitCode()).isNotZero();
        assertThat(jdbc.queryForObject("SELECT ativo FROM canal_credencial", Boolean.class)).isFalse();
    }

    @Test
    void ativacaoSemBypassRlsERecusadaAntesDeSuporBancoVazio() throws Exception {
        assertThat(cadastrar("CADASTRAR").getExitCode()).isZero();
        String papel = "operador_zapi_" + UUID.randomUUID().toString().replace("-", "");
        administrativo("CREATE ROLE " + papel + " LOGIN");
        try {
            var resultado = POSTGRES.execInContainer("psql", "-X", "-U", papel, "-d", banco,
                    "-v", "banco_esperado=" + banco, "-v", "credencial_id=" + credencial(),
                    "-v", "confirmar=HOMOLOGACAO_ISOLADA", "-f", "/tmp/ativar-canal-z-api-homologacao.sql");
            assertThat(resultado.getExitCode()).isNotZero();
            assertThat(resultado.getStderr()).contains("BYPASSRLS");
            assertThat(jdbc.queryForObject("SELECT ativo FROM canal_credencial", Boolean.class)).isFalse();
        } finally {
            administrativo("DROP ROLE " + papel);
        }
    }

    private ExecResult cadastrar(String confirmacao) throws Exception {
        return executar("cadastrar-canal-z-api.sql", "confirmar=" + confirmacao);
    }

    private ExecResult cadastrarSemExcecao() {
        try { return cadastrar("CADASTRAR"); }
        catch (Exception erro) { throw new IllegalStateException(erro); }
    }

    private ExecResult ativar(String confirmacao) throws Exception {
        return executar("ativar-canal-z-api-homologacao.sql", "credencial_id=" + credencial(), "confirmar=" + confirmacao);
    }

    private ExecResult executar(String arquivo, String... overrides) throws Exception {
        List<String> argumentos = new ArrayList<>(List.of("psql", "-X", "-U", POSTGRES.getUsername(), "-d", banco,
                "-v", "banco_esperado=" + banco, "-v", "canal_nome=Z-API HML", "-v", "instancia_id=" + INSTANCIA,
                "-v", "telefone=" + TELEFONE));
        for (String parametro : overrides) {
            argumentos.add("-v"); argumentos.add(parametro);
        }
        argumentos.add("-f"); argumentos.add("/tmp/" + arquivo);
        return POSTGRES.execInContainer(argumentos.toArray(String[]::new));
    }

    private UUID credencial() {
        return jdbc.queryForObject("SELECT id FROM canal_credencial WHERE token_ref = 'env://Z_API_TOKEN'", UUID.class);
    }

    private int contar(String tabela) { return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Integer.class); }

    private void administrativo(String sql) throws Exception {
        try (var conexao = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var comando = conexao.createStatement()) { comando.execute(sql); }
    }
}
