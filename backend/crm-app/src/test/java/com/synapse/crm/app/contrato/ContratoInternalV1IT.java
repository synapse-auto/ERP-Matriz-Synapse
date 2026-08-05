package com.synapse.crm.app.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/**
 * Teste de contrato do E07 §2: se um PR mudar a <b>forma</b> de uma resposta de {@code /internal/v1}
 * — campo removido, renomeado, tipo trocado, rota nova ou removida —, este teste falha. Compara
 * contra um snapshot versionado ({@code contrato/internal-v1-snapshot.json}), nunca contra o proprio
 * codigo: senao o teste so valida a si mesmo.
 *
 * <p>O snapshot e deliberadamente reduzido, nao o {@code /v3/api-docs} cru: sem {@code operationId}
 * (nome gerado a partir do metodo Java, muda por colisao com controllers de OUTROS modulos, sem
 * relacao nenhuma com o contrato) nem {@code tags}. O que sobra — rota, parametro, corpo, respostas e
 * os schemas que elas referenciam — e exatamente o que quebraria a Automacao de um filho, e cabe num
 * diff pequeno o bastante para alguem entender o que mudou.
 *
 * <p>Quando uma mudanca de contrato for intencional: rode este teste, copie o bloco "ATUAL" da falha
 * para {@code src/test/resources/contrato/internal-v1-snapshot.json}, e confira no diff do PR que a
 * mudanca e mesmo essa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ContratoInternalV1IT extends PostgresIT {

    private static final String PREFIXO = "/internal/v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;

    @Test
    void formaDoContratoNaoMudouSemAtualizarOSnapshot() throws Exception {
        JsonNode raiz = JSON.readTree(http.getForObject("/v3/api-docs", String.class));
        Object contratoAtual = extrairContratoOrdenado(raiz);
        Object contratoEsperado = normalizar(JSON.readTree(lerSnapshot()));

        // Comparacao estrutural (Map/List/String), nao de texto: a formatacao do
        // JSON no arquivo de snapshot e so para ficar legivel num diff de PR, e nao
        // pode fazer o teste falhar por espaco em branco.
        assertThat(contratoAtual)
                .as(
                        "A forma de /internal/v1 mudou. Se foi intencional, substitua "
                                + "src/test/resources/contrato/internal-v1-snapshot.json pelo JSON abaixo "
                                + "(pretty-print) e confira no diff do PR que a mudanca e essa:%n%s",
                        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(contratoAtual))
                .isEqualTo(contratoEsperado);
    }

    private static String lerSnapshot() throws Exception {
        try (var entrada = new ClassPathResource("contrato/internal-v1-snapshot.json").getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // --- extracao e normalizacao -----------------------------------------

    private static Object extrairContratoOrdenado(JsonNode raiz) throws Exception {
        JsonNode todasAsRotas = raiz.path("paths");
        TreeMap<String, JsonNode> rotasInternas = new TreeMap<>();
        todasAsRotas.fields().forEachRemaining(entrada -> {
            if (entrada.getKey().startsWith(PREFIXO)) {
                rotasInternas.put(entrada.getKey(), entrada.getValue());
            }
        });

        Set<String> schemasReferenciados = new HashSet<>();
        rotasInternas.values().forEach(rota -> coletarRefs(rota, schemasReferenciados));

        JsonNode todosOsSchemas = raiz.path("components").path("schemas");
        // Fecho transitivo: um schema referenciado pode referenciar outro (ex.:
        // AutomationConfigResposta -> ParametroResposta).
        boolean cresceu = true;
        while (cresceu) {
            cresceu = false;
            for (String nome : List.copyOf(schemasReferenciados)) {
                Set<String> novos = new HashSet<>();
                coletarRefs(todosOsSchemas.path(nome), novos);
                if (schemasReferenciados.addAll(novos)) {
                    cresceu = true;
                }
            }
        }

        TreeMap<String, JsonNode> schemasOrdenados = new TreeMap<>();
        schemasReferenciados.forEach(nome -> schemasOrdenados.put(nome, todosOsSchemas.path(nome)));

        TreeMap<String, Object> contrato = new TreeMap<>();
        contrato.put("paths", normalizarMapa(rotasInternas));
        contrato.put("schemas", normalizarMapa(schemasOrdenados));
        return contrato;
    }

    private static void coletarRefs(JsonNode no, Set<String> destino) {
        if (no.isObject()) {
            no.fields().forEachRemaining(entrada -> {
                if ("$ref".equals(entrada.getKey())) {
                    String ref = entrada.getValue().asText();
                    destino.add(ref.substring(ref.lastIndexOf('/') + 1));
                } else {
                    coletarRefs(entrada.getValue(), destino);
                }
            });
        } else if (no.isArray()) {
            no.forEach(item -> coletarRefs(item, destino));
        }
    }

    private static TreeMap<String, Object> normalizarMapa(TreeMap<String, JsonNode> mapa) {
        TreeMap<String, Object> normalizado = new TreeMap<>();
        mapa.forEach((chave, valor) -> normalizado.put(chave, normalizar(valor)));
        return normalizado;
    }

    /**
     * Arvore do OpenAPI sem metadados editoriais ({@code operationId}, tags, resumo, descricao e
     * seguranca declarativa) e com chaves em ordem alfabetica. O snapshot continua protegendo
     * rotas, parametros, corpos, codigos HTTP e schemas — a forma que quebra o consumidor.
     */
    private static Object normalizar(JsonNode no) {
        if (no.isObject()) {
            TreeMap<String, Object> mapa = new TreeMap<>();
            no.fields().forEachRemaining(entrada -> {
                String chave = entrada.getKey();
                if (!Set.of("operationId", "tags", "summary", "description", "security")
                        .contains(chave)) {
                    mapa.put(chave, normalizar(entrada.getValue()));
                }
            });
            return mapa;
        }
        if (no.isArray()) {
            List<Object> lista = new ArrayList<>();
            no.forEach(item -> lista.add(normalizar(item)));
            return lista;
        }
        if (no.isNull() || no.isMissingNode()) {
            return null;
        }
        if (no.isNumber()) {
            return no.numberValue();
        }
        if (no.isBoolean()) {
            return no.booleanValue();
        }
        return no.asText();
    }
}
