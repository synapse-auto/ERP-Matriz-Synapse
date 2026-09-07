package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
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

    @Test
    @DisplayName("so gestao pode editar e excluir, e acoes usam o id devolvido pelo provedor")
    void gestaoEditaEExcluiEAtendenteRecebe403() throws Exception {
        ResponseEntity<String> criado = chamarComo(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "retorno_orcamento",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola {{1}}"));
        String id = json.readTree(criado.getBody()).path("id").asText();
        assertThat(id).isNotBlank();

        ResponseEntity<String> editadoPorAtendente = chamarComo(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.PUT,
                "/api/v1/whatsapp/templates/" + id,
                Map.of("corpo", "Novo texto {{1}}"));
        assertThat(editadoPorAtendente.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(editadoPorAtendente.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .contains("problem+json");
        assertThat(json.readTree(editadoPorAtendente.getBody()).path("status").asInt())
                .isEqualTo(403);

        ResponseEntity<String> editadoPorGestor = chamarComo(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.PUT,
                "/api/v1/whatsapp/templates/" + id,
                Map.of("corpo", "Novo texto {{1}}"));
        assertThat(editadoPorGestor.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(canal.listarTemplates().getFirst().corpo()).isEqualTo("Novo texto {{1}}");

        ResponseEntity<String> editadoPorSubgestor = chamarComo(
                EMAIL_SUBGESTOR,
                SENHA_SUBGESTOR,
                HttpMethod.PUT,
                "/api/v1/whatsapp/templates/" + id,
                Map.of("corpo", "Texto do subgestor"));
        assertThat(editadoPorSubgestor.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> editadoPorAdministrador = chamarComo(
                EMAIL_ADMINISTRADOR,
                SENHA_ADMINISTRADOR,
                HttpMethod.PUT,
                "/api/v1/whatsapp/templates/" + id,
                Map.of("corpo", "Texto do administrador"));
        assertThat(editadoPorAdministrador.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> excluidoPorAtendente = chamarComo(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/" + id + "?nome=retorno_orcamento",
                null);
        assertThat(excluidoPorAtendente.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> excluidoPorGestor = chamarComo(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/" + id + "?nome=retorno_orcamento",
                null);
        assertThat(excluidoPorGestor.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(canal.listarTemplates()).isEmpty();
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String url, Object corpo) {
        return chamarComo(EMAIL_ANA, SENHA_ATENDENTE, metodo, url, corpo);
    }

    private ResponseEntity<String> chamarComo(
            String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
