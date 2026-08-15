package com.synapse.crm.app.relatorios;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Prova o contrato gerencial pelo HTTP e as fontes temporais do read model. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DashboardVisaoGeralIT extends PostgresIT {

    private static final String PREFIXO = "E20-DASH-";
    private static final String URL = "/api/v1/dashboard/visao-geral?ano=2040&meses=8";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void limparCenario() {
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM avaliacao WHERE atendimento_id IN "
                        + "(SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN "
                        + "(SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @AfterEach
    void limparCenarioDepoisDoTeste() {
        limparCenario();
    }

    @Test
    @DisplayName("métricas usam período, comparação anterior e timeline de GANHO")
    void metricas_reais_eTimelineDoPeriodo() throws Exception {
        UUID ana = idDoUsuario(EMAIL_ANA);
        UUID gestor = idDoUsuario(EMAIL_GESTOR);
        UUID etapaGanha = jdbc.queryForObject(
                "SELECT id FROM etapa_atendimento WHERE resultado='GANHO'", UUID.class);
        UUID etapaEmAndamento = jdbc.queryForObject(
                "SELECT id FROM etapa_atendimento WHERE resultado='EM_ANDAMENTO' ORDER BY ordem LIMIT 1",
                UUID.class);

        Instant agosto = Instant.parse("2040-08-10T13:00:00Z");
        Instant julho = Instant.parse("2040-07-10T13:00:00Z");
        UUID[] atuais = criarLeads(4, "atual", agosto, etapaEmAndamento, ana);
        UUID[] anteriores = criarLeads(4, "anterior", julho, etapaEmAndamento, ana);

        jdbc.update("UPDATE lead SET etapa_atendimento_id=? WHERE id IN (?,?)", etapaGanha, atuais[0], atuais[1]);
        jdbc.update("UPDATE lead SET etapa_atendimento_id=? WHERE id=?", etapaGanha, anteriores[0]);

        UUID atendimentoAtual1 = criarAtendimento(atuais[0], ana, agosto, 10);
        UUID atendimentoAtual2 = criarAtendimento(atuais[1], ana, agosto.plusSeconds(3600), 20);
        criarAtendimento(atuais[2], ana, agosto.plusSeconds(7200), 30);
        criarAtendimento(atuais[3], ana, agosto.plusSeconds(10800), 40);
        UUID atendimentoAnterior1 = criarAtendimento(anteriores[0], ana, julho, 40);
        criarAtendimento(anteriores[1], ana, julho.plusSeconds(3600), 60);

        criarAvaliacao(atendimentoAtual1, ana, 4, agosto.plusSeconds(900));
        criarAvaliacao(atendimentoAtual2, ana, 5, agosto.plusSeconds(4500));
        criarAvaliacao(atendimentoAnterior1, ana, 2, julho.plusSeconds(3600));

        registrarGanho(anteriores[0], ana, gestor, julho.plusSeconds(100));
        registrarGanho(atuais[0], ana, gestor, agosto.plusSeconds(100));
        registrarGanho(atuais[0], ana, gestor, agosto.plusSeconds(200));
        registrarGanho(atuais[1], null, gestor, agosto.plusSeconds(300));

        criarMensagem(atendimentoAtual1, Instant.parse("2040-08-10T17:00:00Z"));
        criarMensagem(atendimentoAtual1, Instant.parse("2040-08-10T17:10:00Z"));
        criarMensagem(atendimentoAtual2, Instant.parse("2040-08-11T17:00:00Z"));
        registrarTransferencia(atendimentoAtual1, atuais[0], gestor, agosto.plusSeconds(50));

        JsonNode resposta = chamarComo(EMAIL_GESTOR, SENHA_GESTOR, URL);

        assertThat(resposta.at("/atendimentos/noPeriodo").asLong()).isEqualTo(4);
        assertThat(resposta.at("/atendimentos/comparativo/valor").decimalValue())
                .isEqualByComparingTo("100.00");
        assertThat(resposta.at("/atendimentos/comparativo/unidade").asText())
                .isEqualTo("PERCENTUAL");
        assertThat(resposta.at("/tempoMedioAtendimento/segundos").asLong()).isEqualTo(1500);
        assertThat(resposta.at("/avaliacaoMedia/media").decimalValue()).isEqualByComparingTo("4.50");
        assertThat(resposta.at("/avaliacaoMedia/escalaMaxima").asInt()).isEqualTo(5);

        // Um dos quatro finalizados teve transferencia: permanece no denominador e sai apenas do
        // numerador. Os dois finalizados de julho nao tiveram transferencia, portanto o comparativo
        // e 75% - 100% = -25 pontos percentuais.
        assertThat(resposta.at("/resolucaoPorIa/atendimentosFinalizados").asLong()).isEqualTo(4);
        assertThat(resposta.at("/resolucaoPorIa/resolvidosSemTransferencia").asLong())
                .isEqualTo(3);
        assertThat(resposta.at("/resolucaoPorIa/percentual").decimalValue())
                .isEqualByComparingTo("75.00");
        assertThat(resposta.at("/resolucaoPorIa/comparativo/valor").decimalValue())
                .isEqualByComparingTo("-25.00");
        assertThat(resposta.at("/resolucaoPorIa/comparativo/unidade").asText())
                .isEqualTo("PONTOS_PERCENTUAIS");

        // O fechamento de julho fica fora; duas transições do mesmo lead em agosto contam uma.
        assertThat(resposta.at("/vendasFechadas/noPeriodo").asLong()).isEqualTo(2);
        assertThat(resposta.at("/taxaConversao/percentual").decimalValue())
                .isEqualByComparingTo("50.00");
        assertThat(resposta.at("/taxaConversao/comparativo/valor").decimalValue())
                .isEqualByComparingTo("25.00");
        assertThat(resposta.at("/taxaConversao/comparativo/unidade").asText())
                .isEqualTo("PONTOS_PERCENTUAIS");

        assertThat(resposta.at("/rankingDeVendas/atendentes/0/id").asText())
                .isEqualTo(ana.toString());
        assertThat(resposta.at("/rankingDeVendas/atendentes/0/vendas").asLong()).isEqualTo(1);
        assertThat(resposta.at("/rankingDeVendas/semResponsavel").asLong()).isEqualTo(1);

        JsonNode etapaGanhaNoFunil = encontrarPorId(resposta.path("funil"), etapaGanha);
        assertThat(etapaGanhaNoFunil.path("quantidade").asLong()).isEqualTo(2);
        assertThat(resposta.path("horarioDePico").get(14).path("quantidade").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("atendente recebe 403; subgestor e administrador recebem 200")
    void acesso_restrito_aGestaoEAdministrador() throws Exception {
        var atendente = ApoioAutenticacao.comToken(
                http,
                ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken(),
                HttpMethod.GET,
                URL,
                String.class);
        assertThat(atendente.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode subgestor = chamarComo(EMAIL_SUBGESTOR, SENHA_SUBGESTOR, URL);
        assertThat(subgestor.at("/periodo/ano").asInt()).isEqualTo(2040);

        JsonNode administrador = chamarComo(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR, URL);
        assertThat(administrador.at("/periodo/ano").asInt()).isEqualTo(2040);
    }

    @Test
    @DisplayName("mês inválido responde Problem Details 400")
    void mesInvalido_responde400() {
        var resposta = ApoioAutenticacao.comToken(
                http,
                ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken(),
                HttpMethod.GET,
                "/api/v1/dashboard/visao-geral?ano=2040&meses=13",
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("Periodo do dashboard invalido");
    }

    private UUID[] criarLeads(
            int quantidade, String grupo, Instant criadoEm, UUID etapaId, UUID responsavelId) {
        UUID[] ids = new UUID[quantidade];
        for (int indice = 0; indice < quantidade; indice++) {
            ids[indice] = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO lead
                        (id, nome, status_basico, etapa_atendimento_id,
                         atendente_responsavel_id, criado_em)
                    VALUES (?, ?, 'EM_ATENDIMENTO', ?, ?, ?)
                    """,
                    ids[indice],
                    PREFIXO + grupo + "-" + indice,
                    etapaId,
                    responsavelId,
                    timestamp(criadoEm));
        }
        return ids;
    }

    private UUID criarAtendimento(UUID leadId, UUID atendenteId, Instant inicio, long minutos) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO atendimento
                    (id, lead_id, atendente_id, status, iniciado_em, finalizado_em)
                VALUES (?, ?, ?, 'FINALIZADO', ?, ?)
                """,
                id,
                leadId,
                atendenteId,
                timestamp(inicio),
                timestamp(inicio.plus(Duration.ofMinutes(minutos))));
        return id;
    }

    private void criarAvaliacao(
            UUID atendimentoId, UUID atendenteId, int nota, Instant criadoEm) {
        jdbc.update(
                "INSERT INTO avaliacao (id, atendimento_id, atendente_id, nota, criado_em) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                atendimentoId,
                atendenteId,
                nota,
                timestamp(criadoEm));
    }

    private void registrarGanho(
            UUID leadId, UUID responsavelId, UUID atorId, Instant criadoEm) {
        String dados = responsavelId == null
                ? "{\"resultado_novo\":\"GANHO\",\"responsavel_id\":null}"
                : "{\"resultado_novo\":\"GANHO\",\"responsavel_id\":\""
                        + responsavelId
                        + "\"}";
        jdbc.update(
                """
                INSERT INTO evento_timeline
                    (id, lead_id, tipo, descricao, origem, ator_id, dados, criado_em)
                VALUES (?, ?, 'ETAPA_ALTERADA', 'teste dashboard', 'USUARIO', ?, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                leadId,
                atorId,
                dados,
                timestamp(criadoEm));
    }

    private void criarMensagem(UUID atendimentoId, Instant enviadoEm) {
        jdbc.update(
                """
                INSERT INTO mensagem
                    (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
                VALUES (?, ?, 'LEAD', 'TEXTO', 'teste', 'ENVIADO', ?)
                """,
                UUID.randomUUID(),
                atendimentoId,
                timestamp(enviadoEm));
    }

    private void registrarTransferencia(
            UUID atendimentoId, UUID leadId, UUID atorId, Instant criadoEm) {
        jdbc.update(
                """
                INSERT INTO evento_timeline
                    (id, lead_id, atendimento_id, tipo, descricao, origem, ator_id, dados, criado_em)
                VALUES (?, ?, ?, 'ATENDIMENTO_TRANSFERIDO', 'teste resolucao IA',
                        'USUARIO', ?, '{}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                leadId,
                atendimentoId,
                atorId,
                timestamp(criadoEm));
    }

    private JsonNode chamarComo(String email, String senha, String url) throws Exception {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        var resposta =
                ApoioAutenticacao.comToken(http, token, HttpMethod.GET, url, String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody());
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, email);
    }

    private static JsonNode encontrarPorId(JsonNode itens, UUID id) {
        for (JsonNode item : itens) {
            if (id.toString().equals(item.path("id").asText())) return item;
        }
        throw new AssertionError("item nao encontrado: " + id);
    }

    private static Timestamp timestamp(Instant instante) {
        return Timestamp.from(instante);
    }
}
