package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ReacoesDeMensagemIT extends PostgresIT {

    private static final String PREFIXO = "E84-REACAO-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private UUID atendimentoId;
    private UUID leadId;
    private UUID anaId;
    private UUID mensagemId;
    private Instant enviadoEm;

    @BeforeEach
    void preparar() {
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");

        anaId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        leadId = UUID.randomUUID();
        atendimentoId = UUID.randomUUID();
        mensagemId = UUID.randomUUID();
        enviadoEm = Instant.parse("2026-08-28T15:00:00Z");
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                leadId,
                PREFIXO + leadId,
                anaId);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                atendimentoId,
                leadId,
                anaId);
        jdbc.update(
                """
                INSERT INTO mensagem
                    (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
                VALUES (?, ?, 'LEAD', 'TEXTO', 'ola', 'ENTREGUE', ?)
                """,
                mensagemId,
                atendimentoId,
                Timestamp.from(enviadoEm));
    }

    @Test
    @DisplayName("definir, substituir, remover e PUT repetido nao duplicam a reacao propria")
    void cicloIdempotente() throws Exception {
        JsonNode definida = corpo(putComo(EMAIL_ANA, SENHA_ATENDENTE, "👍"));
        assertThat(definida.path("reacoes")).hasSize(1);
        assertThat(definida.path("reacoes").get(0).path("emoji").asText()).isEqualTo("👍");
        assertThat(definida.path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(1);
        assertThat(definida.path("reacoes").get(0).path("reagi").asBoolean()).isTrue();

        JsonNode repetida = corpo(putComo(EMAIL_ANA, SENHA_ATENDENTE, "👍"));
        assertThat(repetida.path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(1);
        assertThat(contarLinhas()).isEqualTo(1);

        JsonNode substituida = corpo(putComo(EMAIL_ANA, SENHA_ATENDENTE, "❤️"));
        assertThat(substituida.path("reacoes")).hasSize(1);
        assertThat(substituida.path("reacoes").get(0).path("emoji").asText()).isEqualTo("❤️");
        assertThat(contarLinhas()).isEqualTo(1);

        JsonNode removida = corpo(deleteComo(EMAIL_ANA, SENHA_ATENDENTE));
        assertThat(removida.path("reacoes")).isEmpty();
        assertThat(contarLinhas()).isZero();
        assertThat(deleteComo(EMAIL_ANA, SENHA_ATENDENTE).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("duas pessoas no mesmo emoji contam duas; historico agrega em lote")
    void duasPessoasEHistoricoSemNMaisUm() throws Exception {
        UUID segunda = UUID.randomUUID();
        Instant segundaEm = enviadoEm.plusSeconds(1);
        jdbc.update(
                """
                INSERT INTO mensagem
                    (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
                VALUES (?, ?, 'LEAD', 'TEXTO', 'segunda', 'ENTREGUE', ?)
                """,
                segunda,
                atendimentoId,
                Timestamp.from(segundaEm));

        putComo(EMAIL_ANA, SENHA_ATENDENTE, "👍");
        putComo(EMAIL_GESTOR, SENHA_GESTOR, "👍");
        putComo(EMAIL_ANA, SENHA_ATENDENTE, "🎉", segunda, segundaEm);

        JsonNode pagina = json.readTree(getComo(EMAIL_ANA, SENHA_ATENDENTE,
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens").getBody());
        JsonNode primeira = mensagemNaPagina(pagina, mensagemId);
        JsonNode outra = mensagemNaPagina(pagina, segunda);
        assertThat(primeira.path("reacoes")).hasSize(1);
        assertThat(primeira.path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(2);
        assertThat(primeira.path("reacoes").get(0).path("reagi").asBoolean()).isTrue();
        assertThat(outra.path("reacoes").get(0).path("emoji").asText()).isEqualTo("🎉");
        assertThat(outra.path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(1);
        assertThat(contarLinhas()).isEqualTo(3);
    }

    @Test
    @DisplayName("negativos no ponto de entrada HTTP: 401, visibilidade 404, payload e chave invalidos")
    void negativosHttp() throws Exception {
        ResponseEntity<String> anonimo = http.exchange(
                url(mensagemId, enviadoEm), HttpMethod.PUT, new HttpEntity<>(Map.of("emoji", "👍")), String.class);
        assertThat(anonimo.getStatusCode().value()).isEqualTo(401);

        assertThat(putComo(EMAIL_BRUNO, SENHA_ATENDENTE, "👍").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(contarLinhas()).isZero();

        ResponseEntity<String> payload = putComo(EMAIL_ANA, SENHA_ATENDENTE, "ok");
        assertThat(payload.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(payload.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(contarLinhas()).isZero();

        ResponseEntity<String> inexistente = putComo(
                EMAIL_ANA, SENHA_ATENDENTE, "👍", UUID.randomUUID(), enviadoEm);
        assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(contarLinhas()).isZero();

        ResponseEntity<String> timestamp = putComo(
                EMAIL_ANA, SENHA_ATENDENTE, "👍", mensagemId, enviadoEm.plusSeconds(90));
        assertThat(timestamp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(contarLinhas()).isZero();
    }

    @Test
    @DisplayName("concorrencia da mesma pessoa deixa uma unica reacao, nunca 500")
    void concorrenciaNaoDuplica() throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicReference<org.springframework.http.HttpStatusCode> primeira = new AtomicReference<>();
        AtomicReference<org.springframework.http.HttpStatusCode> segunda = new AtomicReference<>();
        pool.submit(() -> {
            largada.await();
            primeira.set(putComToken(token, "👍").getStatusCode());
            return null;
        });
        pool.submit(() -> {
            largada.await();
            segunda.set(putComToken(token, "❤️").getStatusCode());
            return null;
        });
        largada.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(primeira.get().is2xxSuccessful()).isTrue();
        assertThat(segunda.get().is2xxSuccessful()).isTrue();
        assertThat(contarLinhas()).isEqualTo(1);
    }

    private ResponseEntity<String> putComo(String email, String senha, String emoji) {
        return putComo(email, senha, emoji, mensagemId, enviadoEm);
    }

    private ResponseEntity<String> putComo(String email, String senha, String emoji, UUID id, Instant quando) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return putComToken(token, emoji, id, quando);
    }

    private ResponseEntity<String> putComToken(String token, String emoji) {
        return putComToken(token, emoji, mensagemId, enviadoEm);
    }

    private ResponseEntity<String> putComToken(String token, String emoji, UUID id, Instant quando) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                url(id, quando),
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("emoji", emoji), cabecalhos),
                String.class);
    }

    private ResponseEntity<String> deleteComo(String email, String senha) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        return http.exchange(url(mensagemId, enviadoEm), HttpMethod.DELETE, new HttpEntity<>(cabecalhos), String.class);
    }

    private ResponseEntity<String> getComo(String email, String senha, String caminho) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, caminho, String.class);
    }

    private String url(UUID id, Instant quando) {
        return "/api/v1/atendimentos/" + atendimentoId + "/mensagens/" + id + "/reacao?enviadoEm=" + quando;
    }

    private int contarLinhas() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM mensagem_reacao WHERE mensagem_id = ? OR mensagem_id IN (SELECT id FROM mensagem WHERE atendimento_id = ?)",
                Integer.class,
                mensagemId,
                atendimentoId);
        return n == null ? 0 : n;
    }

    private static JsonNode corpo(ResponseEntity<String> resposta) throws Exception {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new ObjectMapper().readTree(resposta.getBody());
    }

    private static JsonNode mensagemNaPagina(JsonNode pagina, UUID id) {
        for (JsonNode item : pagina.path("mensagens")) {
            if (id.toString().equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("mensagem ausente: " + id);
    }
}
