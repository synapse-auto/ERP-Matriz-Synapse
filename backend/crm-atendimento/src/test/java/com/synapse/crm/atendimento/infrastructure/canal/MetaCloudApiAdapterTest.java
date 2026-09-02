package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.CanalIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.PedidoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.ProvedorTemporariamenteIndisponivelException;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;
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
        assertThat(payload.path("audio").path("voice").asBoolean()).isTrue();
    }

    @Test
    void audioSemLegendaContinuaFuncionando() {
        JsonNode payload = enviarMidia(TipoMensagem.AUDIO, "audio/ogg", null);

        assertThat(payload.path("type").asText()).isEqualTo("audio");
        assertThat(payload.path("audio").path("id").asText()).isEqualTo("media-id");
        assertThat(payload.path("audio").has("caption")).isFalse();
    }

    @Test
    void audioOggSobeComCodecsOpusENotaDeVoz() {
        String corpoUpload = enviarMidiaCapturandoUpload(TipoMensagem.AUDIO, "audio/ogg", "anexo", null);

        assertThat(corpoUpload).contains("audio/ogg; codecs=opus");
        assertThat(corpoUpload).contains("filename=\"anexo.ogg\"");
        assertThat(corpoUpload).contains("Content-Type: audio/ogg");
    }

    @Test
    void audioMp4SobeComContentTypeESemVoice() {
        String metadados = "{\"nome\":\"gravacao.m4a\",\"mimetype\":\"audio/mp4;codecs=mp4a.40.2\"}";
        String[] upload = {null};
        JsonNode payload = enviarMidiaComMetadados(TipoMensagem.AUDIO, metadados, upload);

        assertThat(upload[0]).contains("Content-Type: audio/mp4");
        assertThat(upload[0]).contains("filename=\"gravacao.m4a\"");
        assertThat(upload[0]).doesNotContain("codecs=mp4a");
        assertThat(payload.path("audio").has("voice")).isFalse();
    }

    @Test
    void audioFragmentadoEReconstruidoComoAacAntesDoUpload() {
        byte[] fmp4 = AacAdtsDeIsoBmffTest.fmp4ComUmFrame(new byte[] {0x21, 0x10, 0x04, 0x60});
        when(armazenamento.baixar(REFERENCIA)).thenReturn(fmp4);
        String[] upload = {null};
        enviarMidiaComMetadados(
                TipoMensagem.AUDIO,
                "{\"nome\":\"gravacao.m4a\",\"mimetype\":\"audio/mp4\"}",
                upload);

        assertThat(upload[0]).contains("Content-Type: audio/aac");
        assertThat(upload[0]).contains("filename=\"gravacao.aac\"");
        assertThat(upload[0]).contains("audio/aac");
        assertThat(upload[0]).contains(new String(new byte[] {(byte) 0xFF, (byte) 0xF1}, java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    @Test
    void nomeAusenteRecebeExtensaoDoMime() {
        String corpoUpload = enviarMidiaCapturandoUpload(TipoMensagem.AUDIO, "audio/mp4", null, null);

        assertThat(corpoUpload).contains("filename=\"audio.m4a\"");
        assertThat(corpoUpload).contains("Content-Type: audio/mp4");
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

    @Test
    void videoComLegendaMantemCaption() {
        JsonNode payload = enviarMidia(TipoMensagem.VIDEO, "video/mp4", "Legenda do video");

        assertThat(payload.path("type").asText()).isEqualTo("video");
        assertThat(payload.path("video").path("id").asText()).isEqualTo("media-id");
        assertThat(payload.path("video").path("caption").asText()).isEqualTo("Legenda do video");
    }

    private JsonNode enviarMidia(TipoMensagem tipo, String mimetype, String legenda) {
        return enviarMidiaComMetadados(
                tipo, "{\"nome\":\"anexo\",\"mimetype\":\"" + mimetype + "\"}", null, legenda);
    }

    private String enviarMidiaCapturandoUpload(
            TipoMensagem tipo, String mimetype, String nome, String legenda) {
        String nomeJson = nome == null ? "" : "\"nome\":\"" + nome + "\",";
        String[] upload = {null};
        enviarMidiaComMetadados(
                tipo, "{" + nomeJson + "\"mimetype\":\"" + mimetype + "\"}", upload, legenda);
        return upload[0];
    }

    private JsonNode enviarMidiaComMetadados(
            TipoMensagem tipo, String metadados, String[] uploadCapturado) {
        return enviarMidiaComMetadados(tipo, metadados, uploadCapturado, null);
    }

    private JsonNode enviarMidiaComMetadados(
            TipoMensagem tipo, String metadados, String[] uploadCapturado, String legenda) {
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> {
                    if (uploadCapturado != null) {
                        uploadCapturado[0] = new String(
                                ((MockClientHttpRequest) requisicao).getBodyAsBytes(),
                                java.nio.charset.StandardCharsets.ISO_8859_1);
                    }
                })
                .andRespond(withSuccess("{\"id\":\"media-id\"}", MediaType.APPLICATION_JSON));

        final JsonNode[] payloadCapturado = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(requisicao -> payloadCapturado[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

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
    void respostaIncluiContextMessageId() {
        final JsonNode[] payloadCapturado = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> payloadCapturado[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"id\":\"wamid.resp\"}]}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5561999999999",
                new ConteudoDeEnvio.MensagemLivre("resposta"),
                UUID.randomUUID(),
                "wamid.origem"));

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Aceito.class);
        assertThat(payloadCapturado[0].path("context").path("message_id").asText())
                .isEqualTo("wamid.origem");
        assertThat(payloadCapturado[0].path("text").path("body").asText()).isEqualTo("resposta");
    }

    @Test
    void envioSemContextoNaoInventaContext() {
        final JsonNode[] payloadCapturado = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> payloadCapturado[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5561999999999",
                new ConteudoDeEnvio.MensagemLivre("oi"),
                UUID.randomUUID()));

        servidor.verify();
        assertThat(payloadCapturado[0].has("context")).isFalse();
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
    void contaNegocioAusenteFalhaSemConsultarGraphNemAbrirCircuitBreaker() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidorSemConta = MockRestServiceServer.bindTo(builder).build();
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        CanalProperties semConta = new CanalProperties(
                MetaCloudApiAdapter.PROVEDOR,
                URL_BASE,
                NUMERO,
                "token-de-teste",
                "verify",
                "secret",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "");
        MetaCloudApiAdapter adapterSemConta =
                new MetaCloudApiAdapter(builder, semConta, json, breakers, armazenamento);

        for (int tentativa = 0; tentativa < 10; tentativa++) {
            assertThatThrownBy(adapterSemConta::listarTemplates)
                    .isInstanceOf(CanalIndisponivelException.class)
                    .hasMessageContaining("WHATSAPP_CONTA_NEGOCIO")
                    .hasMessageContaining("WABA ID");
        }

        servidorSemConta.verify();
        assertThat(breakers.circuitBreaker("canal-meta-cloud-templates").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void criarTemplateSemContaNegocioNaoConsultaGraphNemAbreCircuitBreaker() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidorSemConta = MockRestServiceServer.bindTo(builder).build();
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        CanalProperties semConta = new CanalProperties(
                MetaCloudApiAdapter.PROVEDOR,
                URL_BASE,
                NUMERO,
                "token-de-teste",
                "verify",
                "secret",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "");
        MetaCloudApiAdapter adapterSemConta =
                new MetaCloudApiAdapter(builder, semConta, json, breakers, armazenamento);
        PedidoDeTemplate pedido = new PedidoDeTemplate(
                "retorno_orcamento", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola {{1}}");

        for (int tentativa = 0; tentativa < 10; tentativa++) {
            assertThatThrownBy(() -> adapterSemConta.criarTemplate(pedido))
                    .isInstanceOf(CanalIndisponivelException.class)
                    .hasMessageContaining("WHATSAPP_CONTA_NEGOCIO");
        }

        servidorSemConta.verify();
        assertThat(breakers.circuitBreaker("canal-meta-cloud-templates").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void lista400DaMetaViraCanalIndisponivelENaoConsultaCampoWhatsappBusinessAccount() {
        servidor.expect(
                        once(),
                        requestTo(URL_BASE
                                + "/waba-teste/message_templates?limit=100&fields=name,language,status,category,components"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                """
                                {"error":{"code":100,"message":"Tried accessing nonexisting field (whatsapp_business_account)"}}
                                """));

        assertThatThrownBy(adapter::listarTemplates)
                .isInstanceOf(CanalIndisponivelException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("whatsapp_business_account");

        servidor.verify();
    }

    @Test
    void lista429DaMetaViraCanalIndisponivel() {
        servidor.expect(
                        once(),
                        requestTo(URL_BASE
                                + "/waba-teste/message_templates?limit=100&fields=name,language,status,category,components"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"too many requests\"}}"));

        assertThatThrownBy(adapter::listarTemplates)
                .isInstanceOf(CanalIndisponivelException.class)
                .hasMessageContaining("HTTP 429");

        servidor.verify();
    }

    @Test
    void lista500DaMetaViraCanalIndisponivel() {
        servidor.expect(
                        once(),
                        requestTo(URL_BASE
                                + "/waba-teste/message_templates?limit=100&fields=name,language,status,category,components"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"upstream\"}}"));

        assertThatThrownBy(adapter::listarTemplates)
                .isInstanceOf(CanalIndisponivelException.class)
                .hasMessageContaining("HTTP 500");

        servidor.verify();
    }

    @Test
    void timeoutAoListarViraCanalIndisponivel() {
        servidor.expect(
                        once(),
                        requestTo(URL_BASE
                                + "/waba-teste/message_templates?limit=100&fields=name,language,status,category,components"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new ResourceAccessException("read timed out");
                });

        assertThatThrownBy(adapter::listarTemplates).isInstanceOf(CanalIndisponivelException.class);

        servidor.verify();
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
        assertThat(payloadCapturado[0].path("parameter_format").asText()).isEqualTo("positional");
        assertThat(payloadCapturado[0].path("components").get(0).path("example").path("body_text").get(0))
                .hasSize(2);
        assertThat(payloadCapturado[0]
                        .path("components")
                        .get(0)
                        .path("example")
                        .path("body_text")
                        .get(0)
                        .get(0)
                        .asText())
                .isEqualTo("Maria");
    }

    @Test
    void recusaTemplateComMensagemDaMetaSemContarComoQuedaDoProvedor() {
        servidor.expect(once(), requestTo(URL_BASE + "/waba-teste/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                """
                                {"error":{"code":100,"message":"Invalid parameter",
                                "error_user_msg":"O exemplo nao pode ser um placeholder."}}
                                """));

        var resultado = adapter.criarTemplate(new PedidoDeTemplate(
                "exemplo_template",
                "pt_BR",
                TemplateDoCanal.Categoria.UTILIDADE,
                "teste de template {{1}}"));

        servidor.verify();
        assertThat(resultado.aceito()).isFalse();
        assertThat(resultado).isInstanceOf(ResultadoDeTemplate.Recusado.class);
        assertThat(((ResultadoDeTemplate.Recusado) resultado).motivo())
                .isEqualTo("O exemplo nao pode ser um placeholder.");
    }

    @Test
    void criaTemplateQuandoMetaDevolveJsonComoTextPlain() {
        servidor.expect(once(), requestTo(URL_BASE + "/waba-teste/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"id\":\"123\",\"status\":\"PENDING\",\"category\":\"UTILITY\"}",
                        MediaType.TEXT_PLAIN));

        var resultado = adapter.criarTemplate(new PedidoDeTemplate(
                "retorno_orcamento",
                "pt_BR",
                TemplateDoCanal.Categoria.UTILIDADE,
                "Ola {{1}}, {{2}}, {{3}} e {{4}}."));

        servidor.verify();
        assertThat(resultado.aceito()).isTrue();
    }

    @Test
    void recusa400ComoTextPlainContinuaRecusaDeterministica() {
        servidor.expect(once(), requestTo(URL_BASE + "/waba-teste/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(
                                """
                                {"error":{"code":100,"message":"Invalid parameter",
                                "error_user_msg":"O exemplo nao pode ser um placeholder."}}
                                """));

        var resultado = adapter.criarTemplate(new PedidoDeTemplate(
                "exemplo_template",
                "pt_BR",
                TemplateDoCanal.Categoria.UTILIDADE,
                "teste de template {{1}}"));

        servidor.verify();
        assertThat(resultado.aceito()).isFalse();
        assertThat(((ResultadoDeTemplate.Recusado) resultado).motivo())
                .isEqualTo("O exemplo nao pode ser um placeholder.");
    }

    @Test
    void recusaDeterministicaNaoAbreOBreakerDeTemplates() {
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidorLocal = MockRestServiceServer.bindTo(builder).build();
        for (int i = 0; i < 10; i++) {
            servidorLocal
                    .expect(requestTo(URL_BASE + "/waba-teste/message_templates"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"error_user_msg\":\"nome invalido\"}}"));
        }
        MetaCloudApiAdapter local = new MetaCloudApiAdapter(
                builder,
                new CanalProperties(
                        MetaCloudApiAdapter.PROVEDOR,
                        URL_BASE,
                        NUMERO,
                        "token-de-teste",
                        "verify",
                        "secret",
                        Duration.ofHours(24),
                        Duration.ofSeconds(10),
                        "waba-teste"),
                json,
                breakers,
                armazenamento);
        PedidoDeTemplate pedido = new PedidoDeTemplate(
                "retorno_orcamento", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola {{1}}");

        for (int i = 0; i < 10; i++) {
            assertThat(local.criarTemplate(pedido).aceito()).isFalse();
        }

        servidorLocal.verify();
        assertThat(breakers.circuitBreaker("canal-meta-cloud-templates").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void jsonDaRespostaCapturaStatusContentTypeECorpoSemExporToken() {
        var bruta = new MetaCloudApiAdapter.RespostaBrutaDaMeta(
                200,
                "text/plain;charset=UTF-8",
                "{\"id\":\"123\",\"status\":\"PENDING\"}");

        JsonNode no = adapter.jsonDaRespostaDeTemplate(bruta, "criar");

        assertThat(bruta.status()).isEqualTo(200);
        assertThat(bruta.contentType()).contains("text/plain");
        assertThat(bruta.corpo()).contains("PENDING");
        assertThat(bruta.corpo()).doesNotContain("token-de-teste");
        assertThat(bruta.corpo()).doesNotContain("Bearer");
        assertThat(no.path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void corpoInvalidoRelataStatusContentTypeETrechoSemToken() {
        var bruta = new MetaCloudApiAdapter.RespostaBrutaDaMeta(
                200, "text/html", "<html>ok Bearer token-de-teste</html>");

        assertThatThrownBy(() -> adapter.jsonDaRespostaDeTemplate(bruta, "criar"))
                .isInstanceOf(CanalIndisponivelException.class)
                .hasMessageContaining("HTTP 200")
                .hasMessageContaining("text/html")
                .hasMessageContaining("<html>")
                .hasMessageNotContaining("token-de-teste")
                .hasMessageNotContaining("Bearer token-de-teste");
    }

    @Test
    void corpoVazioRelataStatusEContentType() {
        var bruta = new MetaCloudApiAdapter.RespostaBrutaDaMeta(200, "application/json", "  ");

        assertThatThrownBy(() -> adapter.jsonDaRespostaDeTemplate(bruta, "criar"))
                .isInstanceOf(CanalIndisponivelException.class)
                .hasMessageContaining("corpo vazio")
                .hasMessageContaining("HTTP 200")
                .hasMessageContaining("application/json");
    }

    @Test
    void listaTemplatesQuandoMetaDevolveJsonComoTextPlain() {
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
                        MediaType.TEXT_PLAIN));

        var templates = adapter.listarTemplates();

        servidor.verify();
        assertThat(templates).hasSize(1);
        assertThat(templates.getFirst().nome()).isEqualTo("boas_vindas");
    }

    @Test
    void criaTemplateComQuatroVariaveisEnviaQuatroAmostras() {
        final JsonNode[] payloadCapturado = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + "/waba-teste/message_templates"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> payloadCapturado[0] = json.readTree(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess(
                        "{\"id\":\"123\",\"status\":\"PENDING\"}", MediaType.APPLICATION_JSON));

        adapter.criarTemplate(new PedidoDeTemplate(
                "retorno_completo",
                "pt_BR",
                TemplateDoCanal.Categoria.UTILIDADE,
                "Ola {{1}}, {{2}}, {{3}} e {{4}}."));

        servidor.verify();
        assertThat(payloadCapturado[0]
                        .path("components")
                        .get(0)
                        .path("example")
                        .path("body_text")
                        .get(0))
                .hasSize(4);
    }

    @Test
    void identidadeDoCanalComJsonValidoAceitaCredencial() {
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "?fields=id"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"phone-id\"}",
                        new MediaType("application", "json", StandardCharsets.UTF_8)));

        CanalGateway.AutenticacaoDoCanal autenticacao = adapter.verificarAutenticacao();

        servidor.verify();
        assertThat(autenticacao.autenticada()).isTrue();
    }

    @Test
    void identidadeDoCanalComCorpoIlegivelRecusaSemSubirExcecaoELogaACausa() {
        Logger logger = (Logger) LoggerFactory.getLogger(MetaCloudApiAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        servidor.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "?fields=id"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("<html>indisponivel</html>", MediaType.TEXT_HTML));

        CanalGateway.AutenticacaoDoCanal autenticacao;
        try {
            autenticacao = adapter.verificarAutenticacao();
        } finally {
            logger.detachAppender(appender);
        }

        servidor.verify();
        assertThat(autenticacao.autenticada()).isFalse();
        assertThat(autenticacao.detalhe()).contains("IllegalStateException");
        assertThat(appender.list)
                .anySatisfy(evento -> {
                    assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                    assertThat(evento.getThrowableProxy()).isNotNull();
                    assertThat(evento.getThrowableProxy().getClassName())
                            .contains("IllegalStateException");
                    assertThat(evento.getThrowableProxy().getCause().getClassName())
                            .contains("Json");
                });
    }

    @Test
    void sondaDeSaudeFalhandoNaoAbreDisjuntorDeEnvioNemDeMidia() {
        CircuitBreakerRegistry breakers = breakersSensiveis();
        AdaptadorLocal local = novoAdaptador(breakers);
        local.servidor
                .expect(times(5), requestTo(URL_BASE + "/" + NUMERO + "?fields=id"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        esperarEnvioDeTexto(local.servidor);
        esperarDownloadDeMidia(local.servidor, "midia-sonda");

        for (int i = 0; i < 5; i++) {
            assertThat(local.adapter.verificarAutenticacao().autenticada()).isFalse();
        }

        assertThat(breakers.circuitBreaker("canal-meta-cloud-saude").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(enviarTexto(local.adapter).aceito()).isTrue();
        assertThat(local.adapter.baixarMidiaRecebida("midia-sonda").mimetype()).isEqualTo("image/jpeg");
        local.servidor.verify();
        assertThat(breakers.circuitBreaker("canal-meta-cloud").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breakers.circuitBreaker("canal-meta-cloud-midia").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void falhaDeEnvioNaoAbreDisjuntorDeMidia() {
        CircuitBreakerRegistry breakers = breakersSensiveis();
        AdaptadorLocal local = novoAdaptador(breakers);
        local.servidor
                .expect(times(5), requestTo(URL_BASE + "/" + NUMERO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("read timed out");
                });
        esperarDownloadDeMidia(local.servidor, "midia-envio");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> enviarTexto(local.adapter)).isInstanceOf(ResourceAccessException.class);
        }

        assertThat(breakers.circuitBreaker("canal-meta-cloud").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(local.adapter.baixarMidiaRecebida("midia-envio").conteudo()).isEqualTo(new byte[] {1, 2, 3});
        local.servidor.verify();
        assertThat(breakers.circuitBreaker("canal-meta-cloud-midia").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void falhaDeMidiaNaoAbreDisjuntorDeEnvio() {
        CircuitBreakerRegistry breakers = breakersSensiveis();
        AdaptadorLocal local = novoAdaptador(breakers);
        local.servidor
                .expect(times(5), requestTo(URL_BASE + "/midia-falha"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        esperarEnvioDeTexto(local.servidor);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> local.adapter.baixarMidiaRecebida("midia-falha"))
                    .isInstanceOf(RuntimeException.class);
        }

        assertThat(breakers.circuitBreaker("canal-meta-cloud-midia").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(enviarTexto(local.adapter).aceito()).isTrue();
        local.servidor.verify();
        assertThat(breakers.circuitBreaker("canal-meta-cloud").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThatThrownBy(() -> local.adapter.baixarMidiaRecebida("midia-falha"))
                .isInstanceOf(ProvedorTemporariamenteIndisponivelException.class);
    }

    private ResultadoDeEnvio enviarTexto(MetaCloudApiAdapter alvo) {
        return alvo.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5561999999999",
                new ConteudoDeEnvio.MensagemLivre("oi"),
                UUID.randomUUID()));
    }

    private void esperarEnvioDeTexto(MockRestServiceServer alvo) {
        alvo.expect(once(), requestTo(URL_BASE + "/" + NUMERO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));
    }

    private void esperarDownloadDeMidia(MockRestServiceServer alvo, String midiaId) {
        alvo.expect(once(), requestTo(URL_BASE + "/" + midiaId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"url\":\"https://cdn.example.test/arquivo.bin\",\"mime_type\":\"image/jpeg\"}",
                        MediaType.APPLICATION_JSON));
        alvo.expect(once(), requestTo("https://cdn.example.test/arquivo.bin"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
    }

    private AdaptadorLocal novoAdaptador(CircuitBreakerRegistry breakers) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer local = MockRestServiceServer.bindTo(builder).build();
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
        return new AdaptadorLocal(
                new MetaCloudApiAdapter(builder, propriedades, json, breakers, armazenamento), local);
    }

    private static CircuitBreakerRegistry breakersSensiveis() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    private record AdaptadorLocal(MetaCloudApiAdapter adapter, MockRestServiceServer servidor) {}
}
