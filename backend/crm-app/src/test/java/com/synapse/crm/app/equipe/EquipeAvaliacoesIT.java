package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class EquipeAvaliacoesIT extends PostgresIT {

    private static final String PREFIXO = "E160-AVALIACOES-";
    private static final String URL = "/api/v1/equipe/avaliacoes";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void limparAntes() {
        jdbc.update(
                "DELETE FROM avaliacao WHERE atendimento_id IN "
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
    void administradorNaoCompoeResumoDeAvaliacoes() throws Exception {
        UUID ana = idDoUsuario(EMAIL_ANA);
        UUID administrador = idDoUsuario(EMAIL_ADMINISTRADOR);
        UUID canal = jdbc.queryForObject("SELECT id FROM canal ORDER BY nome LIMIT 1", UUID.class);
        UUID leadDaAna = criarLead("comum", ana);
        UUID leadDoAdministrador = criarLead("administrador", administrador);
        UUID atendimentoDaAna = criarAtendimento(leadDaAna, ana, canal);
        UUID atendimentoDoAdministrador = criarAtendimento(
                leadDoAdministrador, administrador, canal);
        jdbc.update(
                "INSERT INTO avaliacao(id,atendimento_id,atendente_id,nota) VALUES (?,?,?,?)",
                UUID.randomUUID(),
                atendimentoDaAna,
                ana,
                5);
        jdbc.update(
                "INSERT INTO avaliacao(id,atendimento_id,atendente_id,nota) VALUES (?,?,?,?)",
                UUID.randomUUID(),
                atendimentoDoAdministrador,
                administrador,
                1);

        var resposta = chamarComoGestor();

        assertThat(resposta.at("/mediaGeral").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(resposta.at("/total").asLong()).isEqualTo(1);
        assertThat(resposta.at("/porAtendente").size()).isEqualTo(1);
        assertThat(resposta.at("/porAtendente/0/atendenteId").asText())
                .isEqualTo(ana.toString());
        assertThat(resposta.at("/porAtendente").toString()).doesNotContain(administrador.toString());
    }

    private JsonNode chamarComoGestor() throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        var resposta = ApoioAutenticacao.comToken(http, token, HttpMethod.GET, URL, String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody());
    }

    private UUID criarLead(String marcador, UUID responsavelId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) "
                        + "VALUES (?,?,?,'EM_ATENDIMENTO')",
                id,
                PREFIXO + marcador,
                responsavelId);
        return id;
    }

    private UUID criarAtendimento(UUID leadId, UUID atendenteId, UUID canalId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status) "
                        + "VALUES (?,?,?,?,'EM_ATENDIMENTO')",
                id,
                leadId,
                canalId,
                atendenteId);
        return id;
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, email);
    }

    private void limparCenario() {
        jdbc.update(
                "DELETE FROM avaliacao WHERE atendimento_id IN "
                        + "(SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }
}
