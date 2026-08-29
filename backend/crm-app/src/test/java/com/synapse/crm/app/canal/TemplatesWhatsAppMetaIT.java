package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class TemplatesWhatsAppMetaIT extends PostgresIT {

    private static final AtomicReference<Integer> statusGraph = new AtomicReference<>(200);
    private static final AtomicReference<String> corpoGraph = new AtomicReference<>(
            "{\"data\":[{\"name\":\"boas_vindas\",\"language\":\"pt_BR\",\"status\":\"APPROVED\","
                    + "\"category\":\"UTILITY\",\"components\":[{\"type\":\"BODY\",\"text\":\"Ola {{1}}\"}]}]}");
    private static final AtomicInteger consultasCampoInvalido = new AtomicInteger();
    private static HttpServer provedor;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CircuitBreakerRegistry breakers;

    @DynamicPropertySource
    static void provedorLocal(DynamicPropertyRegistry registro) {
        iniciarProvedor();
        registro.add("synapse.canal.whatsapp.provedor", () -> "meta-cloud");
        registro.add(
                "synapse.canal.whatsapp.url-base",
                () -> "http://127.0.0.1:" + provedor.getAddress().getPort());
        registro.add("synapse.canal.whatsapp.numero-principal", () -> "phone-id-templates");
        registro.add("synapse.canal.whatsapp.token", () -> "token-de-teste");
        registro.add("synapse.canal.whatsapp.conta-negocio", () -> "waba-teste");
    }

    @BeforeEach
    void resetar() {
        breakers.circuitBreaker("canal-meta-cloud-templates").reset();
        statusGraph.set(200);
        corpoGraph.set(
                "{\"data\":[{\"name\":\"boas_vindas\",\"language\":\"pt_BR\",\"status\":\"APPROVED\","
                        + "\"category\":\"UTILITY\",\"components\":[{\"type\":\"BODY\",\"text\":\"Ola {{1}}\"}]}]}");
        consultasCampoInvalido.set(0);
    }

    @AfterAll
    static void parar() {
        if (provedor != null) {
            provedor.stop(0);
        }
    }

    @Test
    @DisplayName("GET valido usa /WABA_ID/message_templates e nunca o campo whatsapp_business_account")
    void listaPeloWaba() throws Exception {
        ResponseEntity<String> resposta = chamar(HttpMethod.GET, "/api/v1/whatsapp/templates", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resposta.getBody()).get(0).path("nome").asText()).isEqualTo("boas_vindas");
        assertThat(consultasCampoInvalido.get()).isZero();
    }

    @Test
    @DisplayName("400 deterministico da Meta nao vira HTTP 500")
    void meta400NaoVira500() throws Exception {
        statusGraph.set(400);
        corpoGraph.set(
                "{\"error\":{\"code\":100,\"message\":\"Tried accessing nonexisting field (whatsapp_business_account)\"}}");

        ResponseEntity<String> resposta = chamar(HttpMethod.GET, "/api/v1/whatsapp/templates", null);

        assertThat(resposta.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode corpo = json.readTree(resposta.getBody());
        assertThat(corpo.path("status").asInt()).isEqualTo(503);
        assertThat(corpo.path("detail").asText()).contains("HTTP 400");
        assertThat(consultasCampoInvalido.get()).isZero();
    }

    @Test
    @DisplayName("429 e 500 da Meta continuam 503 RFC 7807")
    void meta429e500Viram503() throws Exception {
        statusGraph.set(429);
        corpoGraph.set("{\"error\":{\"message\":\"too many requests\"}}");
        ResponseEntity<String> excesso = chamar(HttpMethod.GET, "/api/v1/whatsapp/templates", null);
        assertThat(excesso.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(json.readTree(excesso.getBody()).path("detail").asText()).contains("HTTP 429");

        breakers.circuitBreaker("canal-meta-cloud-templates").reset();
        statusGraph.set(500);
        corpoGraph.set("{\"error\":{\"message\":\"upstream\"}}");
        ResponseEntity<String> queda = chamar(HttpMethod.GET, "/api/v1/whatsapp/templates", null);
        assertThat(queda.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(queda.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(json.readTree(queda.getBody()).path("detail").asText()).contains("HTTP 500");
    }

    @Test
    @DisplayName("POST recusado pela Meta devolve 422, nao 500")
    void postRecusadoDevolve422() throws Exception {
        statusGraph.set(400);
        corpoGraph.set(
                "{\"error\":{\"code\":100,\"message\":\"Invalid parameter\",\"error_user_msg\":\"O exemplo nao pode ser um placeholder.\"}}");

        ResponseEntity<String> resposta = chamar(
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "retorno_orcamento",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola {{1}}"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(resposta.getBody()).path("status").asInt()).isEqualTo(422);
        assertThat(resposta.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private static synchronized void iniciarProvedor() {
        if (provedor != null) {
            return;
        }
        try {
            provedor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provedor.createContext("/", troca -> {
                String caminho = troca.getRequestURI().getPath();
                if (caminho.contains("whatsapp_business_account")) {
                    consultasCampoInvalido.incrementAndGet();
                }
                byte[] corpo = corpoGraph.get().getBytes(StandardCharsets.UTF_8);
                int status = statusGraph.get();
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(status, corpo.length);
                troca.getResponseBody().write(corpo);
                troca.close();
            });
            provedor.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
