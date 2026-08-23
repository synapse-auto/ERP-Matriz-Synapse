package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
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

/** Prova o seed operacional contra o schema completo, sem depender do seed de desenvolvimento. */
@Testcontainers
class SeedDemonstracaoIT {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withFileSystemBind(
                            localizarArquivo("docker/provisionamento/seed-demonstracao.sql")
                                    .toString(),
                            "/tmp/seed-demonstracao.sql",
                            BindMode.READ_ONLY);

    private String banco;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoMigradoEProvisionado() throws Exception {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        banco = "seed_demo_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);

        String url = url(banco);
        Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("telefone_ddi_padrao", "55"))
                .load()
                .migrate();
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        provisionarPreRequisitosComIdsNaoFixos();
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("seed de demonstracao roda apos todas as migrations e e idempotente")
    void seedAposTodasAsMigrationsRodaInteiroEReexecutaSemDuplicar() throws Exception {
        Container.ExecResult primeiraExecucao = executarSeed();
        Container.ExecResult segundaExecucao = executarSeed();

        assertThat(primeiraExecucao.getExitCode())
                .withFailMessage(primeiraExecucao.getStderr())
                .isZero();
        assertThat(segundaExecucao.getExitCode())
                .withFailMessage(segundaExecucao.getStderr())
                .isZero();

        assertThat(contar("usuario", "id::text LIKE 'd4000000-%'"))
                .as("atendentes")
                .isEqualTo(4);
        assertThat(contar(
                        "disponibilidade_atendente_ia",
                        "atendente_id::text LIKE 'd4000000-%' AND disponivel_para_ia"))
                .as("disponibilidades para a IA")
                .isEqualTo(4);
        assertThat(contar("lead", "id::text LIKE 'de000000-%'"))
                .as("leads")
                .isEqualTo(6);
        assertThat(contar("atendimento", "id::text LIKE 'da000000-%'"))
                .as("atendimentos")
                .isEqualTo(4);
        assertThat(contar("mensagem", "id::text LIKE 'd1000000-%'"))
                .as("mensagens")
                .isEqualTo(11);
        assertThat(contar("lembrete", "id::text LIKE 'db000000-%'"))
                .as("lembretes")
                .isEqualTo(2);
        assertThat(contar("mensagem_programada", "id::text LIKE 'd3000000-%'"))
                .as("mensagens programadas")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("seed cria telefone com DDI, conversas e carga desigual para o rodizio")
    void seedCriaDadosCoerentesParaAsTelasEOManualDoRodizio() throws Exception {
        Container.ExecResult execucao = executarSeed();
        assertThat(execucao.getExitCode()).withFailMessage(execucao.getStderr()).isZero();

        Integer telefonesInvalidos = jdbc.queryForObject(
                """
                SELECT count(*) FROM lead
                 WHERE id::text LIKE 'de000000-%'
                   AND telefone !~ '^55[0-9]{10,11}$'
                """,
                Integer.class);
        assertThat(telefonesInvalidos).isZero();

        List<Integer> cargas = jdbc.queryForList(
                """
                SELECT count(a.id)::int
                  FROM usuario u
                  LEFT JOIN atendimento a
                    ON a.atendente_id = u.id AND a.status = 'EM_ATENDIMENTO'
                 WHERE u.id::text LIKE 'd4000000-%'
                 GROUP BY u.id
                 ORDER BY count(a.id) DESC, u.id
                """,
                Integer.class);
        assertThat(cargas).containsExactly(2, 1, 0, 0);

        Integer conversasSemOsDoisLados = jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM atendimento a
                 WHERE a.id::text LIKE 'da000000-%'
                   AND NOT (
                       EXISTS (SELECT 1 FROM mensagem m
                                WHERE m.atendimento_id = a.id AND m.remetente_tipo = 'LEAD')
                       AND EXISTS (SELECT 1 FROM mensagem m
                                   WHERE m.atendimento_id = a.id AND m.remetente_tipo = 'ATENDENTE')
                   )
                """,
                Integer.class);
        assertThat(conversasSemOsDoisLados).isZero();
    }

    private Container.ExecResult executarSeed() throws Exception {
        return POSTGRES.execInContainer(
                "psql",
                "-v",
                "ON_ERROR_STOP=1",
                "-U",
                POSTGRES.getUsername(),
                "-d",
                banco,
                "-f",
                "/tmp/seed-demonstracao.sql");
    }

    private void provisionarPreRequisitosComIdsNaoFixos() {
        UUID canal = UUID.randomUUID();
        UUID credencial = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO canal (id, nome, tipo, ativo) VALUES (?, 'WhatsApp Teste', 'WHATSAPP', TRUE)",
                canal);
        jdbc.update(
                """
                INSERT INTO canal_credencial
                    (id, canal_id, numero, identificador_externo, token_ref, ativo)
                VALUES (?, ?, '5561900000000', '123456789012345', 'env://WHATSAPP_TOKEN', TRUE)
                """,
                credencial,
                canal);

        inserirEtapa("Entrada", 1, "EM_ANDAMENTO");
        inserirEtapa("Qualificacao", 2, "EM_ANDAMENTO");
        inserirEtapa("Proposta", 3, "EM_ANDAMENTO");
        inserirEtapa("Negociacao", 4, "EM_ANDAMENTO");
        inserirEtapa("Venda", 5, "GANHO");
        inserirEtapa("Pos-venda", 6, "EM_ANDAMENTO");
        inserirEtapa("Perdido", 7, "PERDIDO");
    }

    private void inserirEtapa(String nome, int ordem, String resultado) {
        jdbc.update(
                """
                INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual, resultado)
                VALUES (?, ?, ?, '#64748B', ?::resultado_etapa)
                """,
                UUID.randomUUID(),
                nome,
                ordem,
                resultado);
    }

    private int contar(String tabela, String condicao) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE " + condicao, Integer.class);
    }

    private void executarNoBancoAdministrativo(String sql) throws Exception {
        try (Connection conexao = DriverManager.getConnection(
                        url("postgres"), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private static Path localizarArquivo(String relativoAoRepositorio) {
        Path atual = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (atual != null) {
            Path candidato = atual.resolve(relativoAoRepositorio);
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
            atual = atual.getParent();
        }
        throw new IllegalStateException("Arquivo nao encontrado: " + relativoAoRepositorio);
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
