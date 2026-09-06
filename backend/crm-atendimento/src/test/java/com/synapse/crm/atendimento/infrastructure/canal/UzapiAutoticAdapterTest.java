package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class UzapiAutoticAdapterTest {

    private static final String URL_BASE = "https://api.uzapi.example.test";
    private static final String NUMERO = "phone-123";
    private static final String USUARIO = "clinica";
    private static final String VERSAO = "v1";
    private static final String TOKEN = "token-secreto";
    private static final String REFERENCIA = "midias/anexo";

    private final ObjectMapper json = new ObjectMapper();
    private final ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);

    private MockRestServiceServer servidor;
    private UzapiAutoticAdapter adapter;

    @BeforeEach
    void configurar() {
        when(armazenamento.baixar(REFERENCIA)).thenReturn(new byte[] {1, 2, 3});
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        CanalProperties propriedades = new CanalProperties(
                UzapiAutoticAdapter.PROVEDOR,
                URL_BASE,
                NUMERO,
                TOKEN,
                "verify",
                "secret",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "",
                USUARIO,
                VERSAO);
        adapter = new UzapiAutoticAdapter(
                builder,
                propriedades,
                json,
                CircuitBreakerRegistry.ofDefaults(),
                armazenamento);
    }

    @Test
    void identificaOProvedorEAceitaTextoLivre() {
        assertThat(adapter.provedor()).isEqualTo("uzapi-autotic");
        assertThat(adapter.aceitaTextoLivre(java.util.Optional.empty(), java.time.Instant.now()))
                .isTrue();
        assertThat(adapter.exigeTemplateForaDaJanela()).isFalse();
    }

    @Test
    void camposEspecificosTemDefaultsVaziosNoConstrutorLegado() {
        CanalProperties propriedades = new CanalProperties(
                "meta-cloud", null, null, null, null, null, null, null, null);

        assertThat(propriedades.usuarioApi()).isEmpty();
        assertThat(propriedades.versaoApi()).isEmpty();
    }

    @Test
    void enviaTextoComContratoVersionadoEAutenticacaoBearer() throws Exception {
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(content().json("""
                        {"to":"5511999999999","type":"text","text":{"body":"Ola"},
                         "context":{"message_id":"wamid-origem"}}
                        """))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"messages\":[{\"id\":\"wamid.1\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "+55 (11) 99999-9999",
                new ConteudoDeEnvio.MensagemLivre("Ola"),
                UUID.randomUUID(),
                "wamid-origem"));

        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Aceito("wamid.1"));
        servidor.verify();
    }

    @Test
    void enviaMidiaEmUploadEDepoisReferenciaMediaId() {
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(content().string(Matchers.containsString("name=\"messaging_product\"")))
                .andExpect(content().string(Matchers.containsString("name=\"file\"")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"media-1\"}"));
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"to\":\"5511999999999\",\"type\":\"image\","
                                + "\"image\":{\"id\":\"media-1\",\"caption\":\"Legenda\"}}"))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"messages\":[{\"id\":\"wamid.media\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5511999999999",
                new ConteudoDeEnvio.MensagemMidia(
                        TipoMensagem.IMAGEM,
                        REFERENCIA,
                        "{\"nome\":\"foto.jpg\",\"mimetype\":\"image/jpeg\"}",
                        "Legenda"),
                UUID.randomUUID()));

        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Aceito("wamid.media"));
        servidor.verify();
    }

    @Test
    void audioNaoRecebeCaptionNaoDocumentada() {
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/media"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"audio-1\"}"));
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                .andExpect(content().string(Matchers.containsString("\"type\":\"audio\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("caption"))))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"messages\":[{\"id\":\"wamid.audio\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5511999999999",
                new ConteudoDeEnvio.MensagemMidia(
                        TipoMensagem.AUDIO,
                        REFERENCIA,
                        "{\"mimetype\":\"audio/mpeg\"}",
                        "Legenda ignorada"),
                UUID.randomUUID()));

        assertThat(resultado.aceito()).isTrue();
        servidor.verify();
    }

    @Test
    void referenciaPublicaUsaLinkSemUpload() {
        String url = "https://cdn.example.test/foto.jpg";
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                .andExpect(content().json(
                        "{\"to\":\"5511999999999\",\"type\":\"image\","
                                + "\"image\":{\"link\":\""
                                + url
                                + "\",\"caption\":\"Legenda\"}}"))
                .andRespond(withSuccess(
                        "{\"status\":\"success\",\"messages\":[{\"id\":\"wamid.link\"}]}",
                        MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5511999999999",
                new ConteudoDeEnvio.MensagemMidia(
                        TipoMensagem.IMAGEM,
                        url,
                        "{\"mimetype\":\"image/jpeg\"}",
                        "Legenda"),
                UUID.randomUUID()));

        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Aceito("wamid.link"));
        org.mockito.Mockito.verify(armazenamento, never()).baixar(url);
        servidor.verify();
    }

    @Test
    void enviaVideoEDocumentoComOsTiposDocumentados() {
        for (TipoMensagem tipo : List.of(TipoMensagem.VIDEO, TipoMensagem.DOCUMENTO)) {
            String campo = tipo == TipoMensagem.VIDEO ? "video" : "document";
            servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/media"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(Matchers.containsString("name=\"file\"")))
                    .andRespond(withStatus(HttpStatus.CREATED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"id\":\"media-" + campo + "\"}"));
            servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().string(Matchers.containsString("\"type\":\"" + campo + "\"")))
                    .andExpect(content().string(Matchers.containsString("\"id\":\"media-" + campo + "\"")))
                    .andRespond(withSuccess(
                            "{\"status\":\"success\",\"messages\":[{\"id\":\"wamid."
                                    + campo
                                    + "\"}]}",
                            MediaType.APPLICATION_JSON));
        }

        for (TipoMensagem tipo : List.of(TipoMensagem.VIDEO, TipoMensagem.DOCUMENTO)) {
            String campo = tipo == TipoMensagem.VIDEO ? "video" : "document";
            ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                    UUID.randomUUID(),
                    "5511999999999",
                    new ConteudoDeEnvio.MensagemMidia(
                            tipo,
                            REFERENCIA,
                            "{\"nome\":\"arquivo\",\"mimetype\":\"application/octet-stream\"}",
                            "legenda"),
                    UUID.randomUUID()));

            assertThat(resultado.aceito()).isTrue();
        }
        servidor.verify();
    }

    @Test
    void templateERecusadoSemChamadaAoProvedor() {
        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                "5511999999999",
                ConteudoDeEnvio.MensagemTemplate.de("boas_vindas", "pt_BR"),
                UUID.randomUUID()));

        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Recusado(
                "uzapi-autotic nao gerencia templates", true));
        servidor.verify();
    }

    @Test
    void resposta2xxSemStatusOuIdERecusaPermanente() {
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                .andRespond(withSuccess("{\"messages\":[{}]}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(envioTexto());

        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Recusado(
                "resposta de sucesso do provedor invalida: id da mensagem ausente", true));
        servidor.verify();
    }

    @Test
    void qualquer4xxInclusive429ERecusaPermanente() {
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        ResultadoDeEnvio resultado = adapter.enviar(envioTexto());

        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Recusado(
                "provedor recusou a requisicao com HTTP 429", true));
        servidor.verify();
    }

    @Test
    void erro5xxERecusaTemporaria() {
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/messages"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        ResultadoDeEnvio resultado = adapter.enviar(envioTexto());

        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Recusado(
                "provedor indisponivel com HTTP 502", false));
        servidor.verify();
    }

    @Test
    void autenticaConsultandoInstanciaERecusa401SemExporToken() {
        servidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/instance"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThat(adapter.verificarAutenticacao().autenticada()).isTrue();
        servidor.verify();

        RestClient.Builder segundoBuilder = RestClient.builder();
        MockRestServiceServer segundoServidor = MockRestServiceServer.bindTo(segundoBuilder).build();
        CanalProperties propriedades = new CanalProperties(
                UzapiAutoticAdapter.PROVEDOR,
                URL_BASE,
                NUMERO,
                TOKEN,
                null,
                null,
                null,
                null,
                null,
                USUARIO,
                VERSAO);
        UzapiAutoticAdapter segundo = new UzapiAutoticAdapter(
                segundoBuilder,
                propriedades,
                json,
                CircuitBreakerRegistry.ofDefaults(),
                armazenamento);
        segundoServidor.expect(once(), requestTo(URL_BASE + "/clinica/v1/phone-123/instance"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CanalGateway.AutenticacaoDoCanal recusada = segundo.verificarAutenticacao();

        assertThat(recusada.autenticada()).isFalse();
        assertThat(recusada.detalhe()).doesNotContain(TOKEN);
        segundoServidor.verify();
    }

    @Test
    void recebimentoAindaNaoInvestigadoERecusadoExplicitamente() {
        assertThatThrownBy(() -> adapter.baixarMidiaRecebida("media-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("recebimento uzapi-autotic ainda nao investigado");
    }

    @Test
    void localizacaoNaoETratadaComoMidia() {
        assertThatThrownBy(() -> UzapiAutoticAdapter.tipoDoProvedor(TipoMensagem.LOCALIZACAO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LOCALIZACAO nao e midia transferida");
    }

    private CanalGateway.Envio envioTexto() {
        return new CanalGateway.Envio(
                UUID.randomUUID(),
                "5511999999999",
                new ConteudoDeEnvio.MensagemLivre("oi"),
                UUID.randomUUID());
    }
}
