package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.PedidoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class MetaCloudApiAdapterTest {

    private static final String URL_BASE = "https://graph.example.test/v21.0";
    private static final String NUMERO = "numero-meta";
    private static final String REFERENCIA = "midias/anexo";

    private final ObjectMapper json = new ObjectMapper();
    private final ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);

    private MockRestServiceServer servidor;
    private MetaCloudApiAdapter adapter;

    @BeforeEach
    void configurar() {
        when(armazenamento.baixar(REFERENCIA)).thenReturn(new byte[] {1, 2, 3});
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        CanalProperties propriedades = new CanalProperties(
                MetaCloudApiAdapter.PROVEDOR,
                URL_BASE,
                NUMERO,
                "token-de-teste",
                "verify",
                "secret",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "waba-teste");
        adapter = new MetaCloudApiAdapter(
                builder,
                propriedades,
                json,
                CircuitBreakerRegistry.ofDefaults(),
                armazenamento);
    }

    @Test
    void audioComLegendaNaoEnviaCaption() {
        JsonNode payload = enviarMidia(TipoMensagem.AUDIO, "audio/ogg", "Explicacao do audio");

        assertThat(payload.path("type").asText()).isEqualTo("audio");
        assertThat(payload.path("audio").path("id").asText()).isEqualTo("media-id");
        assertThat(payload.path("audio").has("caption")).isFalse();
    }

    @Test
    void audioSemLegendaContinuaFuncionando() {
        JsonNode payload = enviarMidia(TipoMensagem.AUDIO, "audio/ogg", null);

        assertThat(payload.path("type").asText()).isEqualTo("audio");
        assertThat(payload.path("audio").path("id").asText()).isEqualTo("media-id");
        assertThat(payload.path("audio").has("caption")).isFalse();
    }

    @Test
    void imagemComLegendaMantemCaption() {
        JsonNode payload = enviarMidia(TipoMensagem.IMAGEM, "image/png", "Legenda da imagem");

        assertThat(payload.path("type").asText()).isEqualTo("image");
        assertThat(payload.path("image").path("caption").asText()).isEqualTo("Legenda da imagem");
    }

    @Test
    void documentoComLegendaMantemCaption() {
        JsonNode payload = enviarMidia(TipoMensagem.DOCUMENTO, "application/pdf", "Nota do documento");

        assertThat(payload.path("type").asText()).isEqualTo("document");
        assertThat(payload.path("document").path("caption").asText()).isEqualTo("Nota do documento");
    }

    private JsonNode enviarMidia(TipoMensagem tipo, String mimetype, String legenda) {
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "/media"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"media-id\"}", MediaType.APPLICATION_JSON));

        final JsonNode[] payloadCapturado = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(requisicao -> payloadCapturado[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        String metadados = "{\"nome\":\"anexo\",\"mimetype\":\"" + mimetype + "\"}";
        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5561999999999",
                new ConteudoDeEnvio.MensagemMidia(tipo, REFERENCIA, metadados, legenda),
                UUID.randomUUID()));

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Aceito.class);
        return payloadCapturado[0];
    }

    @Test
    void listaTemplatesPeloIdDaContaDeNegocio() {
        servidor.expect(
                        once(),
                        requestTo(URL_BASE
                                + "/waba-teste/message_templates?limit=100&fields=name,language,status,category,components"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"data":[{"name":"boas_vindas","language":"pt_BR","status":"APPROVED",
                        "category":"UTILITY","components":[{"type":"BODY","text":"Ola {{1}}"}]}]}
                        """,
                        MediaType.APPLICATION_JSON));

        var templates = adapter.listarTemplates();

        servidor.verify();
        assertThat(templates).hasSize(1);
        assertThat(templates.getFirst().nome()).isEqualTo("boas_vindas");
        assertThat(templates.getFirst().status()).isEqualTo(TemplateDoCanal.Status.APROVADO);
        assertThat(templates.getFirst().quantidadeDeParametros()).isEqualTo(1);
    }

    @Test
    void criaTemplateDeTextoNoEndpointDaMeta() {
        final JsonNode[] payloadCapturado = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + "/waba-teste/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> payloadCapturado[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        "{\"id\":\"123\",\"status\":\"PENDING\",\"category\":\"UTILITY\"}",
                        MediaType.APPLICATION_JSON));

        var resultado = adapter.criarTemplate(new PedidoDeTemplate(
                "retorno_orcamento",
                "pt_BR",
                TemplateDoCanal.Categoria.UTILIDADE,
                "Orcamento {{1}} ficou pronto, {{2}}."));

        servidor.verify();
        assertThat(resultado.aceito()).isTrue();
        assertThat(payloadCapturado[0].path("name").asText()).isEqualTo("retorno_orcamento");
        assertThat(payloadCapturado[0].path("category").asText()).isEqualTo("UTILITY");
        assertThat(payloadCapturado[0].path("components").get(0).path("example").path("body_text").get(0))
                .hasSize(2);
    }
}
