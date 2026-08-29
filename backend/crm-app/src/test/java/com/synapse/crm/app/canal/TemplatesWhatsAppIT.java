package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.canal.whatsapp.provedor=fake")
class TemplatesWhatsAppIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CanalFake canal;

    @AfterEach
    void limpar() {
        canal.limpar();
    }

    @Test
    @DisplayName("atendente cria e lista template no provedor da instancia")
    void criarEListarTemplate() throws Exception {
        ResponseEntity<String> criado = chamar(
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "retorno_orcamento",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola {{1}}, o orcamento ficou pronto."));

        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode corpo = json.readTree(criado.getBody());
        assertThat(corpo.path("nome").asText()).isEqualTo("retorno_orcamento");
        assertThat(corpo.path("status").asText()).isEqualTo("APROVADO");
        assertThat(corpo.path("quantidadeDeParametros").asInt()).isEqualTo(1);

        ResponseEntity<String> lista = chamar(HttpMethod.GET, "/api/v1/whatsapp/templates", null);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(lista.getBody()).get(0).path("nome").asText()).isEqualTo("retorno_orcamento");
    }

    @Test
    @DisplayName("nome invalido vira 400 e nao chega ao provedor")
    void nomeInvalidoNaoChegaAoProvedor() {
        ResponseEntity<String> resposta = chamar(
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "Oi Cliente!",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(canal.listarTemplates()).isEmpty();
    }

    @Test
    @DisplayName("variavel ausente no corpo vira 400 antes de chegar ao provedor")
    void variavelAusenteNaoChegaAoProvedor() {
        ResponseEntity<String> resposta = chamar(
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "retorno_orcamento",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola {{1}} e {{3}}"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("{{2}}");
        assertThat(canal.listarTemplates()).isEmpty();
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
