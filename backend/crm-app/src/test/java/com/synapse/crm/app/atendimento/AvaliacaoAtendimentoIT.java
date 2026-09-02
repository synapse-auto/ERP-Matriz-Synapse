package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Coleta 0–10 pelo HTTP humano e pela Automacao, incluindo os negativos de visibilidade. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=token-avaliacao-csat")
class AvaliacaoAtendimentoIT extends PostgresIT {

    private static final String PREFIXO = "E-aval-";
    private static final String TOKEN = "token-avaliacao-csat";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID idAna;

    @BeforeEach
    void preparar() {
        limpar();
        idAna = idDoUsuario(EMAIL_ANA);
    }

    @AfterEach
    void limpar() {
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("POST avalia conversa finalizada visivel e GET devolve a mesma nota")
    void registrar_finalizadoVisivel_gravaNota() {
        UUID atendimentoId = finalizarComoAna();

        var criado = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/avaliacao",
                Map.of("nota", 5, "comentario", "rapido"));

        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criado.getBody()).contains("\"nota\":5").contains("rapido");
        assertThat(jdbc.queryForObject(
                        "SELECT nota FROM avaliacao WHERE atendimento_id = ?", Integer.class, atendimentoId))
                .isEqualTo(5);

        var consulta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.GET,
                "/api/v1/atendimentos/" + atendimentoId + "/avaliacao",
                null);
        assertThat(consulta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consulta.getBody()).contains("\"nota\":5");
    }

    @Test
    @DisplayName("atendente nao alcanca avaliacao do colega — 404 e nada gravado")
    void registrar_leadDeColega_retorna404() {
        UUID atendimentoId = finalizarComoAna();

        var resposta = chamar(
                EMAIL_BRUNO,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/avaliacao",
                Map.of("nota", 1));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM avaliacao WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isZero();
    }

    @Test
    @DisplayName("segunda nota no mesmo atendimento responde 409")
    void registrar_duplicada_retorna409() {
        UUID atendimentoId = finalizarComoAna();
        chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/avaliacao",
                Map.of("nota", 4));

        var segunda = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/avaliacao",
                Map.of("nota", 2));

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM avaliacao WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("avaliar conversa aberta responde 422 e nao grava")
    void registrar_aindaAberto_retorna422() {
        UUID lead = criarLead("aberto " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/avaliacao",
                Map.of("nota", 5));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM avaliacao WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isZero();
    }

    @Test
    @DisplayName("nota 11 e rejeitada com 422 (faixa 0–10)")
    void registrar_notaForaDaFaixa_retorna422() {
        UUID atendimentoId = finalizarComoAna();

        var resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/avaliacao",
                Map.of("nota", 11));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM avaliacao WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isZero();
    }

    @Test
    @DisplayName("Automacao grava Bom=7 e Otimo=10 (EV-08) com X-Synapse-Token")
    void automacao_gravaNotasDoContratoEv08() {
        UUID atendimentoBom = finalizarComoAna();
        UUID atendimentoOtimo = finalizarComoAna();

        HttpHeaders internos = new HttpHeaders();
        internos.set("X-Synapse-Token", TOKEN);
        internos.setContentType(MediaType.APPLICATION_JSON);

        var bom = http.exchange(
                "/internal/v1/atendimentos/" + atendimentoBom + "/avaliacao",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("nota", 7), internos),
                String.class);
        assertThat(bom.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject(
                        "SELECT nota FROM avaliacao WHERE atendimento_id = ?", Integer.class, atendimentoBom))
                .isEqualTo(7);

        var otimo = http.exchange(
                "/internal/v1/atendimentos/" + atendimentoOtimo + "/avaliacao",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("nota", 10), internos),
                String.class);
        assertThat(otimo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject(
                        "SELECT nota FROM avaliacao WHERE atendimento_id = ?", Integer.class, atendimentoOtimo))
                .isEqualTo(10);
    }

    @Test
    @DisplayName("Automacao grava CSAT com X-Synapse-Token; JWT humano nao abre o interno")
    void automacao_gravaEJwtNaoAbreInterno() {
        UUID atendimentoId = finalizarComoAna();

        HttpHeaders internos = new HttpHeaders();
        internos.set("X-Synapse-Token", TOKEN);
        internos.setContentType(MediaType.APPLICATION_JSON);
        var criado = http.exchange(
                "/internal/v1/atendimentos/" + atendimentoId + "/avaliacao",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("nota", 3), internos),
                String.class);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject(
                        "SELECT nota FROM avaliacao WHERE atendimento_id = ?", Integer.class, atendimentoId))
                .isEqualTo(3);

        String jwt = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders humanos = new HttpHeaders();
        humanos.setBearerAuth(jwt);
        humanos.setContentType(MediaType.APPLICATION_JSON);
        var recusado = http.exchange(
                "/internal/v1/atendimentos/" + atendimentoId + "/avaliacao",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("nota", 5), humanos),
                String.class);
        assertThat(recusado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private UUID finalizarComoAna() {
        UUID lead = criarLead("final " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);
        var finalizar = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/finalizar",
                null);
        assertThat(finalizar.getStatusCode()).isEqualTo(HttpStatus.OK);
        return atendimentoId;
    }

    private UUID criarAtendimentoViaEnvio(UUID leadId) {
        var resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/mensagens",
                Map.of("leadId", leadId.toString(), "conteudo", "inicio"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return UUID.fromString(resposta.getBody().replaceAll(".*\"atendimentoId\":\"([^\"]+)\".*", "$1"));
    }

    private ResponseEntity<String> chamar(
            String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private UUID criarLead(String nome, UUID dono, Instant ultimaInteracao) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em,"
                        + " ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?, ?)",
                id,
                PREFIXO + nome,
                dono,
                Timestamp.from(ultimaInteracao),
                Timestamp.from(ultimaInteracao));
        return id;
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
