package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.campanhas.application.TemplatesDoCanal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class TemplatesWhatsAppMetaIT extends PostgresIT {

    private static final AtomicReference<Integer> statusGraph = new AtomicReference<>(200);
    private static final AtomicReference<String> corpoGraph = new AtomicReference<>(
            "{\"data\":[{\"name\":\"boas_vindas\",\"language\":\"pt_BR\",\"status\":\"APPROVED\","
                    + "\"category\":\"UTILITY\",\"components\":[{\"type\":\"BODY\",\"text\":\"Ola {{1}}\"}]}]}");
    private static final AtomicInteger consultasCampoInvalido = new AtomicInteger();
    /** "METODO caminho?query" de cada chamada recebida — nunca o cabecalho de autorizacao. */
    private static final List<String> requisicoes = new CopyOnWriteArrayList<>();
    private static final AtomicReference<String> contentTypeGraph = new AtomicReference<>("application/json");
    private static HttpServer provedor;
    private static final AtomicReference<CountDownLatch> liberarResposta = new AtomicReference<>();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CircuitBreakerRegistry breakers;

    @Autowired
    private TemplatesDoCanal catalogoCampanhas;

    @Autowired
    private JdbcTemplate jdbc;

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
        registro.add("synapse.canal.whatsapp.timeout", () -> "2s");
    }

    @BeforeEach
    void resetar() {
        breakers.circuitBreaker("canal-meta-cloud-templates").reset();
        statusGraph.set(200);
        corpoGraph.set(
                "{\"data\":[{\"name\":\"boas_vindas\",\"language\":\"pt_BR\",\"status\":\"APPROVED\","
                        + "\"category\":\"UTILITY\",\"components\":[{\"type\":\"BODY\",\"text\":\"Ola {{1}}\"}]}]}");
        consultasCampoInvalido.set(0);
        requisicoes.clear();
        contentTypeGraph.set("application/json");
        liberarResposta.set(null);
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

    @Test
    @DisplayName("POST com JSON em text/plain da Meta cria o template e nao vira 503")
    void postJsonComoTextPlainCriaTemplate() throws Exception {
        statusGraph.set(200);
        contentTypeGraph.set("text/plain; charset=utf-8");
        corpoGraph.set("{\"id\":\"123\",\"status\":\"PENDING\",\"category\":\"UTILITY\"}");

        ResponseEntity<String> resposta = chamar(
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "retorno_orcamento",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola {{1}}, {{2}}, {{3}} e {{4}}."));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonNode corpo = json.readTree(resposta.getBody());
        assertThat(corpo.path("nome").asText()).isEqualTo("retorno_orcamento");
        assertThat(corpo.path("quantidadeDeParametros").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("POST com HTML da Meta vira 503 RFC 7807 com status e Content-Type, sem token")
    void postHtmlDaMetaVira503SemExporToken() throws Exception {
        statusGraph.set(200);
        contentTypeGraph.set("text/html");
        corpoGraph.set("<html>Bearer token-de-teste</html>");

        ResponseEntity<String> resposta = chamar(
                HttpMethod.POST,
                "/api/v1/whatsapp/templates",
                Map.of(
                        "nome", "retorno_orcamento",
                        "idioma", "pt_BR",
                        "categoria", "UTILIDADE",
                        "corpo", "Ola {{1}}"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String detalhe = json.readTree(resposta.getBody()).path("detail").asText();
        assertThat(detalhe).contains("HTTP 200");
        assertThat(detalhe).contains("text/html");
        assertThat(detalhe).contains("<html>");
        assertThat(detalhe).doesNotContain("token-de-teste");
        assertThat(detalhe).doesNotContain("Bearer token-de-teste");
    }

    @Test
    @DisplayName("gestao edita pelo ID e exclui a variante com nome exigido pela Meta")
    void gestaoEditaEExcluiPeloContratoDaMeta() throws Exception {
        corpoGraph.set(
                "{\"status\":\"APPROVED\",\"components\":[{\"type\":\"BODY\",\"text\":\"Antigo\"}]}");
        ResponseEntity<String> editado = chamarComo(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.PUT,
                "/api/v1/whatsapp/templates/meta-1",
                Map.of("corpo", "Novo {{1}}"));
        assertThat(editado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        corpoGraph.set("{\"success\":true}");
        ResponseEntity<String> excluido = chamarComo(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas",
                null);
        assertThat(excluido.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(excluido.getBody()).isNull();
        assertThat(requisicoes).contains("POST /meta-1");
        assertThat(requisicoes.stream().filter(r -> r.startsWith("DELETE ")))
                .containsExactly("DELETE /waba-teste/message_templates?hsm_id=meta-1&name=boas_vindas");
    }

    @Test
    @DisplayName("exclusao recusada preserva diagnostico numerico sem mensagem livre da Meta")
    void exclusaoRecusadaDevolve422() throws Exception {
        statusGraph.set(400);
        corpoGraph.set("{\"error\":{\"code\":100,\"error_subcode\":2388000,\"fbtrace_id\":\"trace-sintetico_1\",\"error_user_msg\":\"Bearer token-de-teste conteudo-privado\"}}");

        ResponseEntity<String> resposta = chamarComo(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas",
                null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problema = json.readTree(resposta.getBody());
        assertThat(problema.path("status").asInt()).isEqualTo(422);
        assertThat(problema.path("codigo").asText()).isEqualTo("TEMPLATE_EXCLUSAO_RECUSADO");
        assertThat(problema.path("etapa").asText()).isEqualTo("excluir_template_meta");
        assertThat(problema.path("httpStatusProvedor").asInt()).isEqualTo(400);
        assertThat(problema.path("codigoProvedor").asInt()).isEqualTo(100);
        assertThat(problema.path("subcodigoProvedor").asInt()).isEqualTo(2388000);
        assertThat(problema.path("correlacaoProvedor").asText()).isEqualTo("trace-sintetico_1");
        assertThat(resposta.getBody()).doesNotContain("token-de-teste", "conteudo-privado", "error_user_msg");
        assertThat(requisicoes.stream().filter(r -> r.startsWith("DELETE "))).hasSize(1);
    }

    @Test
    @DisplayName("exclusao com a Meta fora do ar devolve 503 RFC 7807, nao 500")
    void exclusaoComMetaForaDoArDevolve503() throws Exception {
        statusGraph.set(500);
        corpoGraph.set("{\"error\":{\"message\":\"upstream\"}}");

        ResponseEntity<String> resposta = chamarComo(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas",
                null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json.readTree(resposta.getBody()).path("status").asInt()).isEqualTo(503);
        assertThat(requisicoes.stream().filter(r -> r.startsWith("DELETE "))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"success\":false}", "{\"success\":\"true\"}", "[]", "<html>token-de-teste</html>"})
    void resposta200SemConfirmacaoNaoViraSucesso(String corpo) throws Exception {
        corpoGraph.set(corpo);
        ResponseEntity<String> resposta = chamarComo(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas", null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(json.readTree(resposta.getBody()).path("codigo").asText())
                .isEqualTo("TEMPLATE_EXCLUSAO_RESULTADO_INCERTO");
        assertThat(resposta.getBody()).doesNotContain("token-de-teste", "<html>");
        assertThat(requisicoes.stream().filter(r -> r.startsWith("DELETE "))).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"401,CREDENCIAL_RECUSADA", "403,PERMISSAO_RECUSADA", "404,RECURSO_INDISPONIVEL", "400,RECUSADO"})
    void recusaDaMetaNaoViraFaltaDePermissaoNoCrm(int status, String motivo) throws Exception {
        statusGraph.set(status);
        corpoGraph.set("{\"error\":{\"code\":190,\"message\":\"segredo\",\"fbtrace_id\":\"token-de-teste\"}}");
        ResponseEntity<String> resposta = chamarComo(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas", null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode problema = json.readTree(resposta.getBody());
        assertThat(problema.path("codigo").asText()).isEqualTo("TEMPLATE_EXCLUSAO_" + motivo);
        assertThat(problema.path("codigoProvedor").asInt()).isEqualTo(190);
        assertThat(problema.has("correlacaoProvedor")).isFalse();
        assertThat(resposta.getBody()).doesNotContain("segredo", "token-de-teste");
        assertThat(requisicoes.stream().filter(r -> r.startsWith("DELETE "))).hasSize(1);
    }

    @Test
    void erroDentroDe200NaoVira204() throws Exception {
        corpoGraph.set("{\"success\":true,\"error\":{\"code\":100,\"message\":\"nao exibir\"}}");
        ResponseEntity<String> resposta = chamarComo(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas", null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(resposta.getBody()).path("codigoProvedor").asInt()).isEqualTo(100);
        assertThat(resposta.getBody()).doesNotContain("nao exibir");
    }

    @Test
    void exclusaoConfirmadaInvalidaCacheDeCampanhasSemExcluirReferencias() {
        UUID campanha = UUID.randomUUID();
        jdbc.update("INSERT INTO campanha_template (id,nome,template_id,template_nome,template_idioma,template_categoria,template_corpo,limite_diario) VALUES (?,?,'meta-1','boas_vindas','pt_BR','UTILIDADE',?,1)",
                campanha, "Snapshot sintetico", "Snapshot preservado");
        try {
        catalogoCampanhas.descartarCache();
        assertThat(catalogoCampanhas.listar()).hasSize(1);
        corpoGraph.set("{\"success\":true}");
        assertThat(chamarComo(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        corpoGraph.set("{\"data\":[]}");
        assertThat(catalogoCampanhas.listar()).isEmpty();
        assertThat(requisicoes.stream().filter(r -> r.startsWith("GET /waba-teste/message_templates"))).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT template_corpo FROM campanha_template WHERE id=?", String.class, campanha))
                .isEqualTo("Snapshot preservado");
        } finally {
            jdbc.update("DELETE FROM campanha_template WHERE id=?", campanha);
        }
    }

    @Test
    void timeoutDoClienteHttpRetornaResultadoIncertoSemRepetir() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        liberarResposta.set(liberar);
        try {
            ResponseEntity<String> resposta = chamarComo(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.DELETE,
                    "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas", null);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(json.readTree(resposta.getBody()).path("codigo").asText()).isEqualTo("TEMPLATE_EXCLUSAO_RESULTADO_INCERTO");
            assertThat(requisicoes.stream().filter(r -> r.startsWith("DELETE "))).hasSize(1);
        } finally {
            liberar.countDown();
            liberarResposta.set(null);
        }
    }

    @Test
    void recusaNaoDescartaCatalogoAnteriorDeCampanhas() {
        catalogoCampanhas.descartarCache();
        assertThat(catalogoCampanhas.listar()).hasSize(1);
        statusGraph.set(400);
        corpoGraph.set("{\"error\":{\"code\":100}}");
        assertThat(chamarComo(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.DELETE,
                "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas", null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(catalogoCampanhas.listar()).hasSize(1);
        assertThat(requisicoes.stream().filter(r -> r.startsWith("GET /waba-teste/message_templates"))).hasSize(1);
    }

    @Test
    @DisplayName("atendente nao exclui: 403 e nenhuma chamada chega a Meta")
    void atendenteNaoExcluiNemChegaNaMeta() {
        ResponseEntity<String> resposta = chamar(
                HttpMethod.DELETE, "/api/v1/whatsapp/templates/meta-1?nome=boas_vindas", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(requisicoes).noneMatch(r -> r.startsWith("DELETE "));
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

    private static synchronized void iniciarProvedor() {
        if (provedor != null) {
            return;
        }
        try {
            provedor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provedor.createContext("/", troca -> {
                String caminho = troca.getRequestURI().getPath();
                String query = troca.getRequestURI().getRawQuery();
                requisicoes.add(troca.getRequestMethod() + " " + caminho + (query == null ? "" : "?" + query));
                if (caminho.contains("whatsapp_business_account")) {
                    consultasCampoInvalido.incrementAndGet();
                }
                CountDownLatch liberar = liberarResposta.get();
                if (liberar != null) {
                    try {
                        liberar.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException interrompido) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] corpo = corpoGraph.get().getBytes(StandardCharsets.UTF_8);
                int status = statusGraph.get();
                troca.getResponseHeaders().set("Content-Type", contentTypeGraph.get());
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
