package com.synapse.crm.app.inbox;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato HTTP da composição: participação e visibilidade são verificadas no caminho real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class InboxUnificadaControllerIT extends PostgresIT {
    private static final String MARCADOR = "E63-inbox-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private UUID conversa;
    private UUID ana;
    private UUID bruno;
    private UUID leadDoBruno;
    private UUID atendimentoDoBruno;

    @BeforeEach
    void preparar() {
        ana = usuario(EMAIL_ANA);
        bruno = usuario("bruno@dev.local");
        leadDoBruno = UUID.randomUUID();
        atendimentoDoBruno = UUID.randomUUID();
        UUID canal = jdbc.queryForObject("SELECT id FROM canal ORDER BY id LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                leadDoBruno, MARCADOR + "lead", bruno);
        jdbc.update("INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                atendimentoDoBruno, leadDoBruno, canal, bruno);
        conversa = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_conversa(id,tipo) VALUES (?, 'DIRETA')", conversa);
        jdbc.update("INSERT INTO chat_interno_participante(conversa_id,usuario_id) VALUES (?,?), (?,?)",
                conversa, ana, conversa, bruno);
        jdbc.update("INSERT INTO chat_interno_mensagem(id,conversa_id,remetente_id,tipo,conteudo) VALUES (?, ?, ?, 'TEXTO', ?)",
                UUID.randomUUID(), conversa, bruno, MARCADOR + "mensagem");
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM chat_interno_conversa WHERE id = ?", conversa);
        jdbc.update("DELETE FROM atendimento WHERE id = ?", atendimentoDoBruno);
        jdbc.update("DELETE FROM lead WHERE id = ?", leadDoBruno);
    }

    @Test
    @DisplayName("participante recebe item discriminado e campos de cliente não aparecem")
    void participante_recebeEquipeNaInbox() throws Exception {
        JsonNode corpo = json.readTree(listarComo(EMAIL_ANA, SENHA_ATENDENTE));
        JsonNode item = encontrar(corpo, conversa.toString());

        assertThat(item.path("tipo").asText()).isEqualTo("EQUIPE_INTERNA");
        assertThat(item.path("conversaId").asText()).isEqualTo(conversa.toString());
        assertThat(item.has("leadId")).isFalse();
        assertThat(item.has("atendimentoId")).isFalse();
    }

    @Test
    @DisplayName("não participante, mesmo gestor, não recebe conversa interna")
    void gestorNaoParticipante_naoVeConversa() throws Exception {
        JsonNode corpo = json.readTree(listarComo(EMAIL_GESTOR, SENHA_GESTOR));
        assertThat(encontrarOpcional(corpo, conversa.toString())).isFalse();

        JsonNode administrador = json.readTree(listarComo(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR));
        assertThat(encontrarOpcional(administrador, conversa.toString())).isFalse();
    }

    @Test
    @DisplayName("a inbox mantém a visibilidade do cliente e só mistura equipe em TODOS")
    void preservaVisibilidadeDeClienteEVisao() throws Exception {
        JsonNode todos = json.readTree(listarComo(EMAIL_ANA, SENHA_ATENDENTE));
        assertThat(ids(todos)).doesNotContain(atendimentoDoBruno.toString());

        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        String ativos = ApoioAutenticacao.comToken(http, token, HttpMethod.GET,
                "/api/v1/atendimentos/inbox?visao=ATIVOS&limite=50", String.class).getBody();
        assertThat(encontrarOpcional(json.readTree(ativos), conversa.toString())).isFalse();
    }

    @Test
    @DisplayName("cliente mantém os campos exigidos pelo card da inbox")
    void clienteMantemContratoDoCartao() throws Exception {
        JsonNode corpo = json.readTree(listarComo("bruno@dev.local", SENHA_ATENDENTE));
        JsonNode item = encontrar(corpo, atendimentoDoBruno.toString());

        assertThat(item.path("tipo").asText()).isEqualTo("CLIENTE");
        assertThat(item.path("leadNome").asText()).isEqualTo(MARCADOR + "lead");
        assertThat(item.path("nome").asText()).isEqualTo(MARCADOR + "lead");
        assertThat(item.path("leadId").asText()).isEqualTo(leadDoBruno.toString());
    }

    @Test
    @DisplayName("endpoint exige autenticação")
    void semToken_devolve401() {
        ResponseEntity<String> resposta = http.exchange("/api/v1/atendimentos/inbox?limite=10",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String listarComo(String email, String senha) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(http, token, HttpMethod.GET,
                "/api/v1/atendimentos/inbox?visao=TODOS&limite=50", String.class).getBody();
    }

    private UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private JsonNode encontrar(JsonNode itens, String id) {
        return java.util.stream.StreamSupport.stream(itens.path("itens").spliterator(), false)
                .filter(item -> id.equals(item.path("conversaId").asText())
                        || id.equals(item.path("atendimentoId").asText()))
                .findFirst().orElseThrow();
    }

    private boolean encontrarOpcional(JsonNode itens, String id) {
        return java.util.stream.StreamSupport.stream(itens.path("itens").spliterator(), false)
                .anyMatch(item -> id.equals(item.path("conversaId").asText()));
    }

    private java.util.Set<String> ids(JsonNode itens) {
        return java.util.stream.StreamSupport.stream(itens.path("itens").spliterator(), false)
                .map(item -> item.path("atendimentoId").asText())
                .collect(java.util.stream.Collectors.toSet());
    }
}
