package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.application.encaminhamentodochat.MontadorDeConteudoDoChatParaCliente;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.equipe.application.chat.MensagemDoChatParaCliente;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

/**
 * O conteúdo que o Chat Interno produz ({@link MontadorDeConteudoDoChatParaCliente}) passando pelos
 * dois adaptadores de provedor de verdade, com o HTTP do provedor simulado: o payload que sai, o
 * upload da mídia e a tradução de falha. É a prova de que nome do arquivo, MIME, legenda e tipo
 * chegam ao provedor e de que os metadados do chat ({@code nome_original}) não se perdem no caminho.
 */
class EncaminhamentoDoChatNosProvedoresTest {

    private static final String REFERENCIA = "midia/objeto-do-chat.bin";
    private static final String NUMERO = "numero-de-teste";
    private static final String META_BASE = "https://graph.example.test/v21.0";
    private static final String UZAPI_BASE = "https://uzapi.example.test";
    private static final String UZAPI_CAMINHO = "/v1/" + NUMERO;

    private final ObjectMapper json = new ObjectMapper();
    private final ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
    private final ConversorDeAudio conversor = mock(ConversorDeAudio.class);
    private final MontadorDeConteudoDoChatParaCliente montador =
            new MontadorDeConteudoDoChatParaCliente(limiteSemConfiguracao(), json);

    private MockRestServiceServer servidorMeta;
    private MockRestServiceServer servidorUzapi;
    private MetaCloudApiAdapter meta;
    private UzapiAutoticAdapter uzapi;

    @BeforeEach
    void configurar() {
        when(armazenamento.baixar(REFERENCIA)).thenReturn(new byte[] {1, 2, 3});
        RestClient.Builder construtorMeta = RestClient.builder();
        servidorMeta = MockRestServiceServer.bindTo(construtorMeta).build();
        meta = new MetaCloudApiAdapter(
                construtorMeta,
                new CanalProperties(MetaCloudApiAdapter.PROVEDOR, META_BASE, NUMERO, "token", "verify", "secret",
                        Duration.ofHours(24), Duration.ofSeconds(10), "waba", "", ""),
                json,
                CircuitBreakerRegistry.ofDefaults(),
                armazenamento,
                conversor);
        RestClient.Builder construtorUzapi = RestClient.builder();
        servidorUzapi = MockRestServiceServer.bindTo(construtorUzapi).build();
        uzapi = new UzapiAutoticAdapter(
                construtorUzapi,
                new CanalProperties(UzapiAutoticAdapter.PROVEDOR, UZAPI_BASE, NUMERO, "token", "verify", "secret",
                        Duration.ofHours(24), Duration.ofSeconds(10), "", "legado", "v1"),
                json,
                CircuitBreakerRegistry.ofDefaults(),
                armazenamento,
                conversor);
    }

    static Stream<Arguments> midias() {
        return Stream.of(
                Arguments.of("IMAGEM", "image/png", "foto.png", "veja a foto", "foto.png"),
                Arguments.of("VIDEO", "video/mp4", "filme.mp4", "veja o vídeo", "filme.mp4"),
                Arguments.of("AUDIO", "audio/ogg", "voz.ogg", null, "voz.ogg"),
                Arguments.of("DOCUMENTO", "application/pdf", "orçamento final.pdf", "segue", "or_amento_final.pdf"));
    }

    @ParameterizedTest(name = "Meta: {0} preserva tipo, MIME, nome e legenda")
    @MethodSource("midias")
    void metaRecebeMidiaDoChat(String tipo, String mime, String nome, String legenda, String nomeEsperado) {
        ConteudoDeEnvio conteudo = montador.montar(midia(tipo, mime, nome, legenda));
        String[] upload = {null};
        servidorMeta.expect(once(), requestTo(META_BASE + "/" + NUMERO + "/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> upload[0] = new String(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes(), StandardCharsets.ISO_8859_1))
                .andRespond(withSuccess("{\"id\":\"media-id\"}", MediaType.APPLICATION_JSON));
        JsonNode[] envio = {null};
        servidorMeta.expect(once(), requestTo(META_BASE + "/" + NUMERO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> envio[0] = json.readTree(((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = meta.enviar(new CanalGateway.Envio(UUID.randomUUID(), "5561999999999", conteudo, UUID.randomUUID()));

        servidorMeta.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Aceito.class);
        verify(armazenamento).baixar(REFERENCIA);
        String campo = campoDoProvedor(tipo);
        assertThat(envio[0].path("type").asText()).isEqualTo(campo);
        assertThat(envio[0].path(campo).path("id").asText()).isEqualTo("media-id");
        assertLegendaENome(envio[0].path(campo), tipo, legenda, nomeEsperado);
        assertThat(upload[0]).contains("Content-Type: " + mime);
    }

    @ParameterizedTest(name = "UZAPI: {0} preserva tipo, MIME, nome e legenda")
    @MethodSource("midias")
    void uzapiRecebeMidiaDoChat(String tipo, String mime, String nome, String legenda, String nomeEsperado) {
        ConteudoDeEnvio conteudo = montador.montar(midia(tipo, mime, nome, legenda));
        String[] upload = {null};
        servidorUzapi.expect(once(), requestTo(UZAPI_BASE + UZAPI_CAMINHO + "/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> upload[0] = new String(
                        ((MockClientHttpRequest) requisicao).getBodyAsBytes(), StandardCharsets.ISO_8859_1))
                .andRespond(withSuccess("{\"id\":\"media-id\"}", MediaType.APPLICATION_JSON));
        JsonNode[] envio = {null};
        servidorUzapi.expect(once(), requestTo(UZAPI_BASE + UZAPI_CAMINHO + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> envio[0] = json.readTree(((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess("{\"status\":\"success\",\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = uzapi.enviar(new CanalGateway.Envio(UUID.randomUUID(), "5561999999999", conteudo, UUID.randomUUID()));

        servidorUzapi.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Aceito.class);
        verify(armazenamento).baixar(REFERENCIA);
        String campo = campoDoProvedor(tipo);
        assertThat(envio[0].path("type").asText()).isEqualTo(campo);
        assertThat(envio[0].path(campo).path("id").asText()).isEqualTo("media-id");
        assertLegendaENome(envio[0].path(campo), tipo, legenda, nomeEsperado);
        assertThat(upload[0]).contains("messaging_product");
    }

    @ParameterizedTest(name = "texto livre do chat vai como texto na {0}")
    @MethodSource("provedores")
    void textoDoChatVaiComoTexto(String provedor) {
        ConteudoDeEnvio conteudo = montador.montar(new MensagemDoChatParaCliente(
                UUID.randomUUID(), UUID.randomUUID(), "TEXTO", "Olá, segue o orçamento.", null, null, null, null, null));
        JsonNode[] envio = {null};
        MockRestServiceServer servidor = provedor.equals("meta") ? servidorMeta : servidorUzapi;
        String url = provedor.equals("meta") ? META_BASE + "/" + NUMERO + "/messages" : UZAPI_BASE + UZAPI_CAMINHO + "/messages";
        servidor.expect(once(), requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requisicao -> envio[0] = json.readTree(((MockClientHttpRequest) requisicao).getBodyAsBytes()))
                .andRespond(withSuccess("{\"status\":\"success\",\"messages\":[{\"id\":\"wamid.t\"}]}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = canal(provedor).enviar(new CanalGateway.Envio(UUID.randomUUID(), "5561999999999", conteudo, UUID.randomUUID()));

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Aceito.class);
        assertThat(envio[0].path("type").asText()).isEqualTo("text");
        assertThat(envio[0].path("text").path("body").asText()).isEqualTo("Olá, segue o orçamento.");
    }

    static Stream<String> provedores() {
        return Stream.of("meta", "uzapi");
    }

    @ParameterizedTest(name = "{0}: HTTP {1} do provedor vira recusa {2}")
    @MethodSource("falhas")
    void falhaDoProvedorETraduzida(String provedor, HttpStatus status, boolean permanente) {
        ConteudoDeEnvio conteudo = montador.montar(midia("IMAGEM", "image/png", "foto.png", "x"));
        MockRestServiceServer servidor = provedor.equals("meta") ? servidorMeta : servidorUzapi;
        String base = provedor.equals("meta") ? META_BASE + "/" + NUMERO : UZAPI_BASE + UZAPI_CAMINHO;
        servidor.expect(once(), requestTo(base + "/media"))
                .andRespond(withSuccess("{\"id\":\"media-id\"}", MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo(base + "/messages")).andRespond(withStatus(status));

        ResultadoDeEnvio resultado = canal(provedor).enviar(new CanalGateway.Envio(UUID.randomUUID(), "5561999999999", conteudo, UUID.randomUUID()));

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(((ResultadoDeEnvio.Recusado) resultado).permanente()).isEqualTo(permanente);
    }

    static Stream<Arguments> falhas() {
        return Stream.of(
                Arguments.of("meta", HttpStatus.BAD_REQUEST, true),
                Arguments.of("meta", HttpStatus.INTERNAL_SERVER_ERROR, false),
                Arguments.of("uzapi", HttpStatus.BAD_REQUEST, true),
                Arguments.of("uzapi", HttpStatus.INTERNAL_SERVER_ERROR, false));
    }

    // ---------------------------------------------------------------- apoio

    private CanalGateway canal(String provedor) {
        return provedor.equals("meta") ? meta : uzapi;
    }

    private static void assertLegendaENome(JsonNode noDaMidia, String tipo, String legenda, String nomeEsperado) {
        if (tipo.equals("AUDIO")) {
            assertThat(noDaMidia.has("caption")).as("audio nao leva legenda").isFalse();
        } else if (legenda != null) {
            assertThat(noDaMidia.path("caption").asText()).isEqualTo(legenda);
        }
        if (tipo.equals("DOCUMENTO")) {
            assertThat(noDaMidia.path("filename").asText()).isEqualTo(nomeEsperado);
        }
    }

    private static String campoDoProvedor(String tipo) {
        return switch (tipo) {
            case "IMAGEM" -> "image";
            case "VIDEO" -> "video";
            case "AUDIO" -> "audio";
            default -> "document";
        };
    }

    private static MensagemDoChatParaCliente midia(String tipo, String mime, String nome, String legenda) {
        return new MensagemDoChatParaCliente(
                UUID.randomUUID(), UUID.randomUUID(), tipo, null, REFERENCIA, nome, mime, 2048L, legenda);
    }

    private static LimiteDeAnexoRepositorio limiteSemConfiguracao() {
        return new LimiteDeAnexoRepositorio() {
            @Override
            public Optional<Long> limiteEmBytes(com.synapse.crm.sharedkernel.midia.CategoriaDeMidia tipo) {
                return Optional.empty();
            }

            @Override
            public Optional<Long> duracaoMaximaAudioEmSegundos() {
                return Optional.empty();
            }
        };
    }
}
