package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class RecorteDaAbaTodosIT extends PostgresIT {

    private static final String PREFIXO = "E106-todos-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private UUID ana;
    private UUID bruno;
    private UUID leadDaAna;
    private UUID atendimentoDaAna;
    private UUID leadFinalizadoDaAna;
    private UUID atendimentoFinalizadoDaAna;
    private UUID leadPotencial;
    private UUID atendimentoPotencial;
    private UUID leadDoBruno;
    private UUID atendimentoDoBruno;
    private UUID leadParticipado;
    private UUID atendimentoParticipado;

    @BeforeEach
    void preparar() {
        ana = usuario(EMAIL_ANA);
        bruno = usuario(EMAIL_BRUNO);

        leadDaAna = lead("proprio", ana, "EM_ATENDIMENTO");
        atendimentoDaAna = atendimento(leadDaAna, ana, "EM_ATENDIMENTO");

        leadFinalizadoDaAna = lead("proprio-finalizado", ana, "FINALIZADO");
        atendimentoFinalizadoDaAna = atendimento(leadFinalizadoDaAna, ana, "FINALIZADO");

        leadPotencial = lead("potencial", null, "IA");
        atendimentoPotencial = atendimento(leadPotencial, null, "EM_IA");

        leadDoBruno = lead("bruno", bruno, "EM_ATENDIMENTO");
        atendimentoDoBruno = atendimento(leadDoBruno, bruno, "EM_ATENDIMENTO");

        leadParticipado = lead("participado", bruno, "EM_ATENDIMENTO");
        atendimentoParticipado = atendimento(leadParticipado, bruno, "EM_ATENDIMENTO");
        jdbc.update(
                "INSERT INTO atendimento_participante(atendimento_id, usuario_id) VALUES (?, ?)",
                atendimentoParticipado,
                ana);
    }

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN "
                        + "(SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    void atendenteVeEmTodosSomentePropriosEParticipadosEMantemPotencialNaAbaCorreta()
            throws Exception {
        String token = token(EMAIL_ANA, SENHA_ATENDENTE);

        JsonNode todos = listar(token, "TODOS");
        assertThat(ids(todos))
                .contains(atendimentoDaAna.toString(), atendimentoFinalizadoDaAna.toString(),
                        atendimentoParticipado.toString())
                .doesNotContain(atendimentoPotencial.toString(), atendimentoDoBruno.toString());
        assertThat(cartao(todos, atendimentoFinalizadoDaAna).path("atendimentoAtivoId").isNull())
                .isTrue();

        assertThat(ids(listar(token, "POTENCIAIS"))).contains(atendimentoPotencial.toString());
    }

    @Test
    void gestorMantemVisaoGeralEmTodos() throws Exception {
        JsonNode todos = listar(token(EMAIL_GESTOR, SENHA_GESTOR), "TODOS");

        assertThat(ids(todos))
                .contains(
                        atendimentoDaAna.toString(),
                        atendimentoFinalizadoDaAna.toString(),
                        atendimentoPotencial.toString(),
                        atendimentoDoBruno.toString(),
                        atendimentoParticipado.toString());
    }

    @Test
    void contadorBateComAListaEmTodasAsAbasParaAtendenteEGestao() throws Exception {
        for (String token : List.of(
                token(EMAIL_ANA, SENHA_ATENDENTE), token(EMAIL_GESTOR, SENHA_GESTOR))) {
            JsonNode contagens = json.readTree(get(token, "/api/v1/atendimentos/contagem"));
            for (String visao : List.of("ATIVOS", "PENDENTES", "POTENCIAIS", "TODOS")) {
                assertThat(contagens.path(visao).asLong())
                        .as("contagem de %s deve usar o mesmo recorte da lista", visao)
                        .isEqualTo(listar(token, visao).size());
            }
        }
    }

    @Test
    void agendaMantemOConjuntoAnteriorParaAtendenteEGestao() throws Exception {
        JsonNode agendaDaAna = json.readTree(get(token(EMAIL_ANA, SENHA_ATENDENTE), "/api/v1/leads"));
        assertThat(idsDeLead(agendaDaAna))
                .contains(leadDaAna.toString(), leadFinalizadoDaAna.toString(),
                        leadPotencial.toString())
                .doesNotContain(leadDoBruno.toString(), leadParticipado.toString());

        JsonNode agendaDaGestao = json.readTree(get(token(EMAIL_GESTOR, SENHA_GESTOR), "/api/v1/leads"));
        assertThat(idsDeLead(agendaDaGestao))
                .contains(
                        leadDaAna.toString(),
                        leadFinalizadoDaAna.toString(),
                        leadPotencial.toString(),
                        leadDoBruno.toString(),
                        leadParticipado.toString());
    }

    private JsonNode listar(String token, String visao) throws Exception {
        return json.readTree(get(token, "/api/v1/atendimentos?visao=" + visao));
    }

    private String get(String token, String rota) {
        return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, rota, String.class)
                .getBody();
    }

    private String token(String email, String senha) {
        return ApoioAutenticacao.login(http, email, senha).accessToken();
    }

    private List<String> ids(JsonNode itens) {
        return java.util.stream.StreamSupport.stream(itens.spliterator(), false)
                .map(item -> item.path("atendimentoId").asText())
                .toList();
    }

    private List<String> idsDeLead(JsonNode itens) {
        return java.util.stream.StreamSupport.stream(itens.spliterator(), false)
                .map(item -> item.path("id").asText())
                .toList();
    }

    private JsonNode cartao(JsonNode itens, UUID atendimentoId) {
        return java.util.stream.StreamSupport.stream(itens.spliterator(), false)
                .filter(item -> atendimentoId.toString().equals(item.path("atendimentoId").asText()))
                .findFirst()
                .orElseThrow();
    }

    private UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID lead(String sufixo, UUID responsavel, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id, nome, atendente_responsavel_id, status_basico) "
                        + "VALUES (?, ?, ?, ?::status_basico_lead)",
                id,
                PREFIXO + sufixo + "-" + id,
                responsavel,
                status);
        return id;
    }

    private UUID atendimento(UUID leadId, UUID responsavel, String status) {
        UUID id = UUID.randomUUID();
        Instant finalizadoEm = "FINALIZADO".equals(status) ? Instant.now() : null;
        jdbc.update(
                "INSERT INTO atendimento(id, lead_id, atendente_id, status, iniciado_em, finalizado_em) "
                        + "VALUES (?, ?, ?, ?::status_atendimento, now(), ?)",
                id,
                leadId,
                responsavel,
                status,
                finalizadoEm == null ? null : Timestamp.from(finalizadoEm));
        return id;
    }
}
