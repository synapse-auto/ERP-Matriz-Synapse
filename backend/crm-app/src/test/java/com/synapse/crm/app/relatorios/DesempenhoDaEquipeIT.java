package com.synapse.crm.app.relatorios;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

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

/** Prova que Equipe e Dashboard consomem a mesma definicao de venda fechada. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DesempenhoDaEquipeIT extends PostgresIT {

    private static final String PREFIXO = "E21-EQUIPE-";
    private static final String URL = "/api/v1/equipe/desempenho";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void preparar() {
        limpar();
    }

    @AfterEach
    void finalizar() {
        limpar();
    }

    @Test
    @DisplayName("gestao ve atendimentos e vendas distintas; venda sem responsavel nao vira linha")
    void agregaDesempenhoComARegraDoDashboard() throws Exception {
        UUID atendenteA = criarAtendente("A");
        UUID atendenteB = criarAtendente("B");
        UUID gestor = idDoUsuario(EMAIL_GESTOR);
        UUID leadA = criarLead("A");
        UUID leadB = criarLead("B");
        UUID leadSemResponsavel = criarLead("SEM-RESPONSAVEL");

        criarAtendimento(leadA, atendenteA);
        criarAtendimento(leadB, atendenteB);
        criarAtendimento(leadSemResponsavel, atendenteB);
        registrarGanho(leadA, atendenteA, gestor, Instant.parse("2041-01-10T12:00:00Z"));
        registrarGanho(leadA, atendenteA, gestor, Instant.parse("2041-01-11T12:00:00Z"));
        registrarGanho(leadB, atendenteB, gestor, Instant.parse("2041-01-12T12:00:00Z"));
        registrarGanho(leadSemResponsavel, null, gestor, Instant.parse("2041-01-13T12:00:00Z"));

        JsonNode resposta = chamarComoGestor();
        JsonNode linhaA = encontrarPorId(resposta.path("porAtendente"), atendenteA);
        JsonNode linhaB = encontrarPorId(resposta.path("porAtendente"), atendenteB);

        assertThat(linhaA.path("atendimentos").asLong()).isEqualTo(1);
        assertThat(linhaA.path("vendas").asLong()).isEqualTo(1);
        assertThat(linhaB.path("atendimentos").asLong()).isEqualTo(2);
        assertThat(linhaB.path("vendas").asLong()).isEqualTo(1);
        assertThat(resposta.toString()).doesNotContain("SEM-RESPONSAVEL");
    }

    @Test
    @DisplayName("atendente recebe 403 e nao obtem vendas de colega pela API")
    void atendenteNaoObtemVendaDeColega() {
        String nomeDoColega = PREFIXO + "COLEGA-SENSIVEL";
        UUID colega = criarAtendente("COLEGA-SENSIVEL");
        UUID lead = criarLead("VENDA-COLEGA");
        registrarGanho(lead, colega, idDoUsuario(EMAIL_GESTOR), Instant.now());

        var resposta = ApoioAutenticacao.comToken(
                http,
                ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken(),
                HttpMethod.GET,
                URL,
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).doesNotContain(nomeDoColega);
    }

    private JsonNode chamarComoGestor() throws Exception {
        var resposta = ApoioAutenticacao.comToken(
                http,
                ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken(),
                HttpMethod.GET,
                URL,
                String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody());
    }

    private UUID criarAtendente(String marcador) {
        UUID id = UUID.randomUUID();
        String hash = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE email=?", String.class, EMAIL_GESTOR);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel) VALUES (?,?,?,?, 'ATENDENTE')",
                id,
                PREFIXO + marcador,
                id + "@e21.invalid",
                hash);
        return id;
    }

    private UUID criarLead(String marcador) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id,nome,status_basico) VALUES (?,?,'EM_ATENDIMENTO')",
                id,
                PREFIXO + marcador);
        return id;
    }

    private void criarAtendimento(UUID leadId, UUID atendenteId) {
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status) VALUES (?,?,?,'FINALIZADO')",
                UUID.randomUUID(),
                leadId,
                atendenteId);
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
                    (id,lead_id,tipo,descricao,origem,ator_id,dados,criado_em)
                VALUES (?,?,'ETAPA_ALTERADA','teste equipe','USUARIO',?,?::jsonb,?)
                """,
                UUID.randomUUID(),
                leadId,
                atorId,
                dados,
                java.sql.Timestamp.from(criadoEm));
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, email);
    }

    private JsonNode encontrarPorId(JsonNode itens, UUID id) {
        for (JsonNode item : itens) {
            if (id.toString().equals(item.path("atendenteId").asText())) {
                return item;
            }
        }
        throw new AssertionError("atendente nao encontrado: " + id);
    }

    private void limpar() {
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
    }
}
