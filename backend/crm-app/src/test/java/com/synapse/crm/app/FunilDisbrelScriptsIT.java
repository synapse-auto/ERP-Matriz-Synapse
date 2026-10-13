package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Prova os scripts do funil da Disbrel (D5b) contra o schema completo, em um banco isolado: simulacao nao grava,
 * aplicacao cria o desenho, e repetir nao muda nada; etapa com lead nao e renomeada nem apagada sem remapeamento
 * explicito; o remapeamento move os leads de forma atomica e o reverter devolve tudo ao estado anterior.
 */
@Testcontainers
class FunilDisbrelScriptsIT {

    private static final String PASTA = "docker/provisionamento/instancias/disbrel/";
    private static final Pattern ID_DA_EXECUCAO = Pattern.compile("guarde para desfazer\\):\\s*([0-9a-f-]{36})");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withFileSystemBind(arquivo("funil-disbrel.sql"), "/tmp/funil-disbrel.sql", BindMode.READ_ONLY)
            .withFileSystemBind(
                    arquivo("funil-disbrel-diagnostico.sql"), "/tmp/funil-disbrel-diagnostico.sql", BindMode.READ_ONLY)
            .withFileSystemBind(
                    arquivo("funil-disbrel-reverter.sql"), "/tmp/funil-disbrel-reverter.sql", BindMode.READ_ONLY);

    private String banco;
    private JdbcTemplate jdbc;

    @BeforeEach
    void criarBancoMigrado() throws Exception {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        banco = "funil_disbrel_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        String url = url(banco);
        Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("telefone_ddi_padrao", "55"))
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("DELETE FROM etapa_atendimento");
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    // --- instancia sem funil -----------------------------------------------------------------------------

    @Test
    @DisplayName("simulacao nao grava nada (nem as tabelas de backup); aplicar cria as cinco etapas; repetir nao muda")
    void simulaAplicaERepeteSemMudar() throws Exception {
        Container.ExecResult simulacao = rodar("funil-disbrel.sql");
        assertThat(simulacao.getExitCode()).withFailMessage(simulacao.getStderr()).isZero();
        assertThat(simulacao.getStdout()).contains("SIMULACAO");
        assertThat(funil()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT to_regclass('funil_disbrel_bkp_etapa')", String.class)).isNull();

        Container.ExecResult aplicacao = rodar("funil-disbrel.sql", "-v", "confirmar=APLICAR");
        assertThat(aplicacao.getExitCode()).withFailMessage(aplicacao.getStderr()).isZero();
        assertThat(funil())
                .containsExactly(
                        "1 Novo Contato EM_ANDAMENTO 0",
                        "2 Aguardando Medidas EM_ANDAMENTO 0",
                        "3 Negociação EM_ANDAMENTO 0",
                        "4 Fechado GANHO 0",
                        "5 Pós-venda EM_ANDAMENTO 0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM funil_disbrel_bkp_etapa", Integer.class)).isZero();

        long etapasAntes = jdbc.queryForObject("SELECT count(*) FROM etapa_atendimento", Long.class);
        Container.ExecResult repeticao = rodar("funil-disbrel.sql", "-v", "confirmar=APLICAR");
        assertThat(repeticao.getExitCode()).withFailMessage(repeticao.getStderr()).isZero();
        assertThat(repeticao.getStdout()).contains("sem mudanca");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM etapa_atendimento", Long.class)).isEqualTo(etapasAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM funil_disbrel_bkp_etapa", Integer.class)).isZero();
    }

    @Test
    @DisplayName("confirmar com valor desconhecido nao faz nada")
    void confirmarInvalidoNaoFazNada() throws Exception {
        Container.ExecResult resultado = rodar("funil-disbrel.sql", "-v", "confirmar=sim");

        assertThat(resultado.getStdout()).contains("confirmar so aceita APLICAR");
        assertThat(funil()).isEmpty();
    }

    // --- instancia com funil e leads ---------------------------------------------------------------------

    @Test
    @DisplayName("etapa com lead nao e renomeada nem apagada: sem remapear, GANHO existente recusa e nada muda")
    void ganhoExistenteRecusaSemRemapeamento() throws Exception {
        funilAntigoComLeads();
        List<String> antes = funil();

        Container.ExecResult resultado = rodar("funil-disbrel.sql", "-v", "confirmar=APLICAR");

        assertThat(resultado.getExitCode()).isNotZero();
        assertThat(resultado.getStderr()).contains("Venda fechada").contains("ja e GANHO");
        assertThat(funil()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT to_regclass('funil_disbrel_bkp_etapa')", String.class)).isNull();
    }

    @Test
    @DisplayName("remapeamento explicito move os leads, apaga a origem, mantem as etapas fora do desenho e e reversivel")
    void remapeiaMantemForaDoDesenhoEReverte() throws Exception {
        funilAntigoComLeads();
        List<String> antes = funil();
        List<String> leadsAntes = leadsPorEtapa();

        String remapear = "{\"Venda fechada\":\"Fechado\",\"Orçamento enviado\":\"Negociacao\"}";
        Container.ExecResult aplicacao = rodar("funil-disbrel.sql", "-v", "confirmar=APLICAR", "-v", "remapear=" + remapear);
        assertThat(aplicacao.getExitCode()).withFailMessage(aplicacao.getStderr()).isZero();

        assertThat(funil())
                .containsExactly(
                        "1 Novo Contato EM_ANDAMENTO 0",
                        "2 Aguardando Medidas EM_ANDAMENTO 0",
                        "3 Negociação EM_ANDAMENTO 5", // 3 dele + 2 do Orcamento enviado
                        "4 Fechado GANHO 4", // os 4 da Venda fechada
                        "5 Pós-venda EM_ANDAMENTO 0",
                        "6 Novo lead EM_ANDAMENTO 1", // fora do desenho: mantida com seu lead
                        "7 Em atendimento EM_ANDAMENTO 2",
                        "8 Perdido PERDIDO 6");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead", Integer.class)).isEqualTo(1 + 2 + 2 + 3 + 4 + 6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM etapa_atendimento WHERE resultado = 'GANHO'", Integer.class))
                .isOne();

        Matcher id = ID_DA_EXECUCAO.matcher(aplicacao.getStdout());
        assertThat(id.find()).as("id da execucao impresso").isTrue();
        Container.ExecResult simulacaoDoReverter = rodar("funil-disbrel-reverter.sql", "-v", "execucao=" + id.group(1));
        assertThat(simulacaoDoReverter.getExitCode()).withFailMessage(simulacaoDoReverter.getStderr()).isZero();
        assertThat(funil()).as("simulacao do reverter nao grava").hasSize(8);

        Container.ExecResult reversao =
                rodar("funil-disbrel-reverter.sql", "-v", "execucao=" + id.group(1), "-v", "confirmar=REVERTER");
        assertThat(reversao.getExitCode()).withFailMessage(reversao.getStderr()).isZero();
        assertThat(funil()).isEqualTo(antes);
        assertThat(leadsPorEtapa()).isEqualTo(leadsAntes);
    }

    @Test
    @DisplayName("reverter nao devolve o lead que um atendente ja moveu depois da execucao")
    void reverterRespeitaLeadMovidoDepois() throws Exception {
        funilAntigoComLeads();
        Container.ExecResult aplicacao = rodar(
                "funil-disbrel.sql", "-v", "confirmar=APLICAR", "-v",
                "remapear={\"Venda fechada\":\"Fechado\"}");
        assertThat(aplicacao.getExitCode()).withFailMessage(aplicacao.getStderr()).isZero();
        Matcher id = ID_DA_EXECUCAO.matcher(aplicacao.getStdout());
        assertThat(id.find()).isTrue();
        UUID movido = jdbc.queryForObject(
                "SELECT lead_id FROM funil_disbrel_bkp_lead LIMIT 1", UUID.class);
        jdbc.update(
                "UPDATE lead SET etapa_atendimento_id = (SELECT id FROM etapa_atendimento WHERE nome = 'Perdido') WHERE id = ?",
                movido);

        Container.ExecResult reversao =
                rodar("funil-disbrel-reverter.sql", "-v", "execucao=" + id.group(1), "-v", "confirmar=REVERTER");

        assertThat(reversao.getExitCode()).withFailMessage(reversao.getStderr()).isZero();
        assertThat(reversao.getStdout()).contains("NAO devolvidos");
        assertThat(jdbc.queryForObject(
                        "SELECT e.nome FROM lead l JOIN etapa_atendimento e ON e.id = l.etapa_atendimento_id WHERE l.id = ?",
                        String.class, movido))
                .isEqualTo("Perdido");
    }

    @Test
    @DisplayName("remapear entre resultados diferentes (mover para GANHO sem venda) e recusado")
    void remapearParaGanhoVindoDeEmAndamentoRecusa() throws Exception {
        funilAntigoComLeads();
        List<String> antes = funil();

        Container.ExecResult resultado = rodar(
                "funil-disbrel.sql", "-v", "confirmar=APLICAR", "-v",
                "remapear={\"Venda fechada\":\"Fechado\",\"Em atendimento\":\"Fechado\"}");

        assertThat(resultado.getExitCode()).isNotZero();
        assertThat(resultado.getStderr()).contains("o resultado precisa ser o mesmo");
        assertThat(funil()).isEqualTo(antes);
    }

    @Test
    @DisplayName("etapa 'Fechado' que nao e GANHO recusa em vez de mudar o que conta como venda")
    void fechadoComResultadoDivergenteRecusa() throws Exception {
        inserirEtapa("Fechado", 1, "EM_ANDAMENTO");
        List<String> antes = funil();

        Container.ExecResult resultado = rodar("funil-disbrel.sql", "-v", "confirmar=APLICAR");

        assertThat(resultado.getExitCode()).isNotZero();
        assertThat(resultado.getStderr()).contains("Fechado").contains("resultado");
        assertThat(funil()).isEqualTo(antes);
    }

    @Test
    @DisplayName("etapa do desenho ja existente com outro nome grafico (sem acento/caixa) e reaproveitada, nao duplicada")
    void reaproveitaEtapaComNomeEquivalente() throws Exception {
        inserirEtapa("NEGOCIACAO", 7, "EM_ANDAMENTO");
        UUID negociacao = jdbc.queryForObject("SELECT id FROM etapa_atendimento", UUID.class);

        Container.ExecResult resultado = rodar("funil-disbrel.sql", "-v", "confirmar=APLICAR");

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM etapa_atendimento", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT ordem FROM etapa_atendimento WHERE id = ?", Integer.class, negociacao))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT nome FROM etapa_atendimento WHERE id = ?", String.class, negociacao))
                .as("o script nunca renomeia")
                .isEqualTo("NEGOCIACAO");
    }

    @Test
    @DisplayName("remover_vazias apaga so etapa EM_ANDAMENTO sem leads fora do desenho; PERDIDO e etapa com lead ficam")
    void removerVaziasPreservaOQueTemLead() throws Exception {
        funilAntigoComLeads();
        inserirEtapa("Etapa vazia", 20, "EM_ANDAMENTO");
        inserirEtapa("Perdido vazio", 21, "PERDIDO");

        Container.ExecResult resultado = rodar(
                "funil-disbrel.sql", "-v", "confirmar=APLICAR", "-v", "remover_vazias=SIM", "-v",
                "remapear={\"Venda fechada\":\"Fechado\"}");

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        List<String> nomes = jdbc.queryForList("SELECT nome FROM etapa_atendimento", String.class);
        assertThat(nomes).doesNotContain("Etapa vazia").contains("Perdido vazio", "Novo lead", "Em atendimento", "Perdido");
    }

    // --- diagnostico -------------------------------------------------------------------------------------

    @Test
    @DisplayName("o diagnostico lista etapas com leads, aponta o conflito de GANHO e nao grava nada")
    void diagnosticoEhSomenteLeitura() throws Exception {
        funilAntigoComLeads();
        List<String> antes = funil();

        Container.ExecResult resultado = rodar("funil-disbrel-diagnostico.sql");

        assertThat(resultado.getExitCode()).withFailMessage(resultado.getStderr()).isZero();
        assertThat(resultado.getStdout())
                .contains("Venda fechada")
                .contains("AUSENTE: o script cria")
                .contains("etapa_que_ja_e_ganho")
                .contains("nada foi gravado");
        assertThat(funil()).isEqualTo(antes);
    }

    // --- apoio -------------------------------------------------------------------------------------------

    /** Funil no formato de funil-padrao.sql, com leads em cada etapa: o caso real mais provavel. */
    private void funilAntigoComLeads() {
        inserirEtapa("Novo lead", 1, "EM_ANDAMENTO");
        inserirEtapa("Em atendimento", 2, "EM_ANDAMENTO");
        inserirEtapa("Orçamento enviado", 3, "EM_ANDAMENTO");
        inserirEtapa("Negociação", 4, "EM_ANDAMENTO");
        inserirEtapa("Venda fechada", 5, "GANHO");
        inserirEtapa("Perdido", 6, "PERDIDO");
        leads("Novo lead", 1);
        leads("Em atendimento", 2);
        leads("Orçamento enviado", 2);
        leads("Negociação", 3);
        leads("Venda fechada", 4);
        leads("Perdido", 6);
    }

    private void inserirEtapa(String nome, int ordem, String resultado) {
        jdbc.update(
                "INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual, resultado) VALUES (?, ?, ?, '#64748B', ?::resultado_etapa)",
                UUID.randomUUID(), nome, ordem, resultado);
    }

    private void leads(String etapa, int quantidade) {
        UUID id = jdbc.queryForObject("SELECT id FROM etapa_atendimento WHERE nome = ?", UUID.class, etapa);
        for (int i = 0; i < quantidade; i++) {
            jdbc.update("INSERT INTO lead (id, nome, etapa_atendimento_id) VALUES (?, ?, ?)", UUID.randomUUID(), etapa + " " + i, id);
        }
    }

    /** "ordem nome resultado leads", em ordem. */
    private List<String> funil() {
        return jdbc.query(
                """
                SELECT e.ordem, e.nome, e.resultado, count(l.id) AS leads
                  FROM etapa_atendimento e LEFT JOIN lead l ON l.etapa_atendimento_id = e.id
                 GROUP BY e.id ORDER BY e.ordem
                """,
                (linha, i) -> linha.getInt("ordem") + " " + linha.getString("nome") + " " + linha.getString("resultado")
                        + " " + linha.getInt("leads"));
    }

    private List<String> leadsPorEtapa() {
        return jdbc.queryForList(
                "SELECT l.nome || ' -> ' || coalesce(e.nome, '-') FROM lead l LEFT JOIN etapa_atendimento e ON e.id = l.etapa_atendimento_id ORDER BY l.nome",
                String.class);
    }

    private Container.ExecResult rodar(String script, String... variaveis) throws Exception {
        List<String> comando = new ArrayList<>(List.of("psql", "-U", POSTGRES.getUsername(), "-d", banco));
        comando.addAll(List.of(variaveis));
        comando.addAll(List.of("-f", "/tmp/" + script));
        return POSTGRES.execInContainer(comando.toArray(String[]::new));
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
            Path candidato = atual.resolve(PASTA + nome);
            if (Files.isRegularFile(candidato)) {
                return candidato.toString();
            }
            atual = atual.getParent();
        }
        throw new IllegalStateException("Arquivo nao encontrado: " + PASTA + nome);
    }

    private static String url(String banco) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + banco;
    }
}
