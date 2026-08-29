package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
class TemplatesWhatsAppSemWabaIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CircuitBreakerRegistry breakers;

    @DynamicPropertySource
    static void semWaba(DynamicPropertyRegistry registro) {
        registro.add("synapse.canal.whatsapp.provedor", () -> "meta-cloud");
        registro.add("synapse.canal.whatsapp.url-base", () -> "http://127.0.0.1:1");
        registro.add("synapse.canal.whatsapp.numero-principal", () -> "phone-id-templates");
        registro.add("synapse.canal.whatsapp.token", () -> "token-de-teste");
        registro.add("synapse.canal.whatsapp.conta-negocio", () -> "");
    }

    @Test
    @DisplayName("GET sem WABA devolve 503 RFC 7807 e nao chama a Graph")
    void getSemWabaDevolve503() throws Exception {
        ResponseEntity<String> resposta = chamar(HttpMethod.GET, "/api/v1/whatsapp/templates", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode corpo = json.readTree(resposta.getBody());
        assertThat(corpo.path("status").asInt()).isEqualTo(503);
        assertThat(corpo.path("detail").asText()).contains("WHATSAPP_CONTA_NEGOCIO");
    }

    @Test
    @DisplayName("POST sem WABA devolve 503 RFC 7807 e nao chama a Graph")
    void postSemWabaDevolve503() throws Exception {
        ResponseEntity<String> resposta = chamar(
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "retorno_orcamento",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola {{1}}"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode corpo = json.readTree(resposta.getBody());
        assertThat(corpo.path("status").asInt()).isEqualTo(503);
        assertThat(corpo.path("detail").asText()).contains("WHATSAPP_CONTA_NEGOCIO");
    }

    @Test
    @DisplayName("dez GET sem WABA nao abrem o circuit breaker de templates")
    void dezChamadasSemWabaNaoAbremBreaker() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            ResponseEntity<String> resposta = chamar(HttpMethod.GET, "/api/v1/whatsapp/templates", null);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(resposta.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        assertThat(breakers.circuitBreaker("canal-meta-cloud-templates").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
