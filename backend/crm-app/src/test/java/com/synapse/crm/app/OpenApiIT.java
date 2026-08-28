package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/** Prova que a documentacao publicada existe, esta aberta e cobre cada operacao HTTP do projeto. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class OpenApiIT extends PostgresIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> VERBOS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private TestRestTemplate http;

    @Test
    void endpointsPublicosDaDocumentacaoRespondemSemToken() {
        assertThat(http.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/v3/api-docs.yaml", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void documentoTemIdentidadeSegurancaECoberturaDeTodasAsOperacoes() throws Exception {
        JsonNode openApi = JSON.readTree(http.getForObject("/v3/api-docs", String.class));

        assertThat(openApi.path("info").path("title").asText()).isEqualTo("Synapse CRM API");
        assertThat(openApi.path("info").path("description").asText()).isNotBlank();
        assertThat(openApi.path("info").path("version").asText()).isEqualTo("v0.1.0");
        assertThat(openApi.path("info").path("x-application-name").asText())
                .isEqualTo("synapse-crm");
        assertThat(openApi.path("components").path("securitySchemes").fieldNames())
                .toIterable()
                .contains("bearerAuth", "synapseToken", "metaWebhookSignature");

        List<String> falhas = falhasDeCobertura(openApi);
        assertThat(falhas).isEmpty();
        assertThat(contarOperacoes(openApi)).isEqualTo(136);
        assertThat(openApi.at("/paths/~1api~1v1~1me/patch/summary").asText())
                .isEqualTo("Atualizar meu perfil");
        assertThat(openApi.at("/paths/~1api~1v1~1whatsapp~1templates/get/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1whatsapp~1templates/post/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1atendimentos~1mensagens~1template/post/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1atendimentos~1novo-contato/post/security/0/bearerAuth").isArray())
                .isTrue();

        assertThat(openApi.at("/paths/~1api~1v1~1leads/get/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1automation-config/get/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1atendimentos~1em-andamento/get/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1leads~1{id}~1tags/post/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1webhook~1canal/post/security/0/metaWebhookSignature").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1auth~1login/post/security").isMissingNode())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1atendimentos~1inbox/get/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1feedbacks/post/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1feedbacks/get/security/0/bearerAuth").isArray())
                .isTrue();
    }

    /** Teste negativo: prova que a verificacao acima realmente acusa uma operacao sem documentacao. */
    @Test
    void verificadorDeCoberturaReprovaOperacaoSemResumoDescricaoTagOuResposta() throws Exception {
        JsonNode incompleto = JSON.readTree(
                """
                {"paths":{"/api/v1/exemplo":{"get":{"responses":{}}}}}
                """);

        assertThat(falhasDeCobertura(incompleto))
                .containsExactly(
                        "GET /api/v1/exemplo sem resumo",
                        "GET /api/v1/exemplo sem descricao",
                        "GET /api/v1/exemplo sem tag",
                        "GET /api/v1/exemplo sem resposta de sucesso");
    }

    private static List<String> falhasDeCobertura(JsonNode openApi) {
        List<String> falhas = new ArrayList<>();
        openApi.path("paths").properties().forEach(rota -> rota.getValue().properties().stream()
                .filter(operacao -> VERBOS.contains(operacao.getKey()))
                .forEach(operacao -> {
                    String rotulo = operacao.getKey().toUpperCase() + " " + rota.getKey();
                    JsonNode detalhes = operacao.getValue();
                    if (detalhes.path("summary").asText().isBlank()) {
                        falhas.add(rotulo + " sem resumo");
                    }
                    if (detalhes.path("description").asText().isBlank()) {
                        falhas.add(rotulo + " sem descricao");
                    }
                    if (detalhes.path("tags").isEmpty()) {
                        falhas.add(rotulo + " sem tag");
                    }
                    boolean temSucesso = detalhes.path("responses").propertyStream()
                            .anyMatch(resposta -> resposta.getKey().startsWith("2"));
                    if (!temSucesso) {
                        falhas.add(rotulo + " sem resposta de sucesso");
                    }
                }));
        return falhas;
    }

    private static long contarOperacoes(JsonNode openApi) {
        return openApi.path("paths").properties().stream()
                .flatMap(rota -> rota.getValue().properties().stream())
                .filter(operacao -> VERBOS.contains(operacao.getKey()))
                .count();
    }
}
