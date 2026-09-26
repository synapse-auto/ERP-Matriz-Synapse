package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
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
        assertThat(contarOperacoes(openApi)).isGreaterThan(0);
        assertThat(chavesDasOperacoes(openApi)).hasSize((int) contarOperacoes(openApi));
        assertThat(nomesDasTags(openApi)).contains("Interno", "Automação", "Resumo por IA");
        assertThat(openApi
                        .at("/paths/~1api~1v1~1atendimentos~1{atendimentoId}~1cartao/get/security/0/bearerAuth")
                        .isArray())
                .isTrue();
        assertThat(openApi
                        .at("/paths/~1api~1v1~1chat-interno~1conversas~1grupo/post/security/0/bearerAuth")
                        .isArray())
                .isTrue();
        assertThat(openApi
                        .at("/paths/~1api~1v1~1chat-interno~1conversas~1{id}~1participantes/get/security/0/bearerAuth")
                        .isArray())
                .isTrue();
        JsonNode download = openApi.at("/paths/~1api~1v1~1chat-interno~1conversas~1{id}~1midias~1{mensagemId}~1arquivo/get");
        assertThat(download.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(download.path("responses").fieldNames()).toIterable().contains("200", "401", "403", "404", "503");
        assertThat(openApi
                        .at("/paths/~1api~1v1~1leads~1{leadId}~1midias~1{mensagemId}~1url/get/security/0/bearerAuth")
                        .isArray())
                .isTrue();
        assertThat(openApi
                        .at("/paths/~1api~1v1~1atendimentos~1destinos-de-transferencia/get/security/0/bearerAuth")
                        .isArray())
                .isTrue();
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
        assertThat(openApi.at("/paths/~1api~1v1~1atendimentos~1leads~1{leadId}~1novo/post/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1config~1canal/get/security/0/bearerAuth").isArray())
                .isTrue();

        assertThat(openApi.at("/paths/~1api~1v1~1leads/get/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1leads~1{id}~1agenda/get/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1automation-config/get/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1atendimentos~1em-andamento/get/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1leads~1{id}~1tags/post/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1leads~1{id}~1foto/post/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1internal~1v1~1leads~1{id}~1foto/delete/security/0/synapseToken").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1leads~1{id}~1foto/get/security/0/bearerAuth").isArray())
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
        assertThat(openApi.at("/paths/~1api~1v1~1atendimentos~1{id}~1mensagens~1{mensagemId}~1reacao/put/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1atendimentos~1{id}~1mensagens~1{mensagemId}~1encaminhamentos/post/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1chat-interno~1conversas~1{id}~1mensagens~1{mensagemId}~1reacao/put/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1automacao~1fidelizacao~1configuracao/get/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.at("/paths/~1api~1v1~1automacao~1fidelizacao~1datas-festivas/get/security/0/bearerAuth").isArray())
                .isTrue();
        assertThat(openApi.path("paths").path("/health/liveness").path("get").path("security").isMissingNode())
                .isTrue();
    }

    @Test
    void documentoIncluiTodosOsInternosEConservaAFrenteDeSeguranca() throws Exception {
        JsonNode openApi = JSON.readTree(http.getForObject("/v3/api-docs", String.class));

        openApi.path("paths").fields().forEachRemaining(entrada -> {
            if (!entrada.getKey().startsWith("/internal/v1/")) {
                return;
            }
            entrada.getValue().fields().forEachRemaining(operacao -> {
                if (!VERBOS.contains(operacao.getKey())) {
                    return;
                }
                assertThat(tags(operacao.getValue()))
                        .as("tags de %s %s", operacao.getKey(), entrada.getKey())
                        .contains("Interno", "Automação");
                assertThat(operacao.getValue().at("/security/0/synapseToken").isArray())
                        .as("seguranca de %s %s", operacao.getKey(), entrada.getKey())
                        .isTrue();
            });
        });

        assertThat(openApi.path("paths").path("/internal/v1/ev05/atendimentos/{atendimentoId}/contexto")
                        .path("get").path("summary").asText())
                .isEqualTo("Consultar contexto de conversa do EV-05");
        assertThat(tags(operacao(openApi, "/internal/v1/ev05/atendimentos/{atendimentoId}/contexto", "get")))
                .contains("Resumo por IA");
    }

    @Test
    void documentoExplicitaTodoOCicloDeResumoEAsChavesDeIdempotencia() throws Exception {
        JsonNode openApi = JSON.readTree(http.getForObject("/v3/api-docs", String.class));

        List<String> ciclo = List.of(
                "GET /internal/v1/ev05/atendimentos/{atendimentoId}/contexto",
                "GET /internal/v1/ev05/leads/{leadId}/resumo",
                "POST /internal/v1/ev05/leads/{leadId}/resumo",
                "POST /internal/v1/ev05/leads/{leadId}/resumo-status",
                "POST /api/v1/atendimentos/{atendimentoId}/resumo-ia",
                "GET /api/v1/atendimentos/{atendimentoId}/resumo-ia",
                "GET /api/v1/automacao/config/resumo-ia",
                "PUT /api/v1/automacao/config/resumo-ia",
                "GET /api/v1/automacao/config/recursos-ia");
        for (String chave : ciclo) {
            String[] partes = chave.split(" ", 2);
            assertThat(operacao(openApi, partes[1], partes[0].toLowerCase()).isObject())
                    .as("endpoint de resumo ausente: %s", chave)
                    .isTrue();
            assertThat(tags(operacao(openApi, partes[1], partes[0].toLowerCase())))
                    .contains("Resumo por IA");
        }

        assertThat(parametro(operacao(openApi, "/internal/v1/ev05/leads/{leadId}/resumo", "post"), "Idempotency-Key")
                        .path("required").asBoolean())
                .isTrue();
        assertThat(parametro(operacao(openApi, "/api/v1/atendimentos/{atendimentoId}/resumo-ia", "post"), "Idempotency-Key")
                        .path("required").asBoolean())
                .isTrue();
    }

    @Test
    void destinosDeTransferenciaDocumentamPapelOpcionalSemAmpliarOsPapeisElegiveis() throws Exception {
        JsonNode openApi = JSON.readTree(http.getForObject("/v3/api-docs", String.class));
        JsonNode operacao = operacao(openApi, "/api/v1/atendimentos/destinos-de-transferencia", "get");
        JsonNode content = operacao.at("/responses/200/content");
        JsonNode media = content.path("*/*");
        if (media.isMissingNode()) {
            media = content.elements().next();
        }
        JsonNode schema = media.path("schema").path("items");
        JsonNode destino = schema;
        if (schema.path("$ref").isTextual()) {
            destino = openApi.at(schema.path("$ref").asText().substring(1));
        }
        JsonNode papel = destino.path("properties").path("papel");
        assertThat(destino.path("required").findValuesAsText("papel")).isEmpty();
        assertThat(papel.path("enum")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("ATENDENTE", "SUBGESTOR");
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

    private static Set<String> chavesDasOperacoes(JsonNode openApi) {
        Set<String> chaves = new HashSet<>();
        openApi.path("paths").fields().forEachRemaining(rota -> rota.getValue().fields().forEachRemaining(operacao -> {
            if (VERBOS.contains(operacao.getKey())) {
                chaves.add(operacao.getKey().toUpperCase() + " " + rota.getKey());
            }
        }));
        return chaves;
    }

    private static Set<String> nomesDasTags(JsonNode openApi) {
        Set<String> nomes = new HashSet<>();
        openApi.path("tags").elements().forEachRemaining(tag -> nomes.add(tag.path("name").asText()));
        return nomes;
    }

    private static JsonNode operacao(JsonNode openApi, String caminho, String verbo) {
        return openApi.path("paths").path(caminho).path(verbo);
    }

    private static JsonNode parametro(JsonNode operacao, String nome) {
        for (JsonNode parametro : operacao.path("parameters")) {
            if (nome.equalsIgnoreCase(parametro.path("name").asText())) {
                return parametro;
            }
        }
        return JSON.createObjectNode();
    }

    private static List<String> tags(JsonNode operacao) {
        List<String> tags = new ArrayList<>();
        operacao.path("tags").elements().forEachRemaining(tag -> tags.add(tag.asText()));
        return tags;
    }
}
