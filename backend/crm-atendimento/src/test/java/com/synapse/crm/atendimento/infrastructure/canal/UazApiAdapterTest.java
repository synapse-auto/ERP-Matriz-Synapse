package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;

class UazApiAdapterTest {

    private static final String URL_BASE = "https://uazapi.example.test";
    private static final String NUMERO = "5561999999999";
    private static final String REFERENCIA = "midias/anexo";

    private final ObjectMapper json = new ObjectMapper();
    private final ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);

    private MockRestServiceServer servidor;
    private UazApiAdapter adapter;

    @BeforeEach
    void configurar() {
        when(armazenamento.baixar(REFERENCIA)).thenReturn(new byte[] {1, 2, 3});
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        CanalProperties propriedades = new CanalProperties(
                UazApiAdapter.PROVEDOR,
                URL_BASE,
                "numero-da-instancia",
                "token-de-teste",
                "verify",
                "secret",
                Duration.ofHours(24),
                Duration.ofSeconds(10),
                "");
        adapter = new UazApiAdapter(
                builder,
                propriedades,
                json,
                CircuitBreakerRegistry.ofDefaults(),
                armazenamento);
    }

    @Test
    void provedorAceitaTextoLivreENaoExigeTemplate() {
        assertThat(adapter.provedor()).isEqualTo("uazapi");
        assertThat(adapter.aceitaTextoLivre(Optional.empty(), java.time.Instant.now())).isTrue();
        assertThat(adapter.exigeTemplateForaDaJanela()).isFalse();
    }

    @Test
    void textoLivreEnviaCorpoExatoELeMessageid() {
        servidor.expect(once(), requestTo(URL_BASE + "/send/text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("token", "token-de-teste"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"number":"5561999999999","text":"Ola cliente"}
                        """))
                .andRespond(withSuccess("{\"messageid\":\"uaz-msg-1\"}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                NUMERO,
                new ConteudoDeEnvio.MensagemLivre("Ola cliente"),
                UUID.randomUUID()));

        servidor.verify();
        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Aceito("uaz-msg-1"));
    }

    @Test
    void respostaUsaReplyidDaMensagemDeContexto() {
        servidor.expect(once(), requestTo(URL_BASE + "/send/text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"number":"5561999999999","text":"resposta","replyid":"uaz-origem"}
                        """))
                .andRespond(withSuccess("{\"messageid\":\"uaz-msg-2\"}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                NUMERO,
                new ConteudoDeEnvio.MensagemLivre("resposta"),
                UUID.randomUUID(),
                "uaz-origem"));

        servidor.verify();
        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Aceito("uaz-msg-2"));
    }

    @Test
    void imagemEnviaArquivoEmBase64() {
        JsonNode payload = enviarMidia(TipoMensagem.IMAGEM, "image/png", "Legenda");

        assertThat(payload.path("type").asText()).isEqualTo("image");
        assertThat(payload.path("file").asText()).isEqualTo("AQID");
        assertThat(payload.path("text").asText()).isEqualTo("Legenda");
        assertThat(payload.path("mimetype").asText()).isEqualTo("image/png");
    }

    @Test
    void videoEnviaArquivoEmBase64() {
        JsonNode payload = enviarMidia(TipoMensagem.VIDEO, "video/mp4", null);

        assertThat(payload.path("type").asText()).isEqualTo("video");
        assertThat(payload.path("file").asText()).isEqualTo("AQID");
        assertThat(payload.has("text")).isFalse();
    }

    @Test
    void documentoEnviaNomeMimetypeEArquivo() {
        JsonNode payload = enviarMidiaComMetadados(
                TipoMensagem.DOCUMENTO,
                "{\"nome\":\"orcamento.pdf\",\"mimetype\":\"application/pdf\"}",
                "Confira");

        assertThat(payload.path("type").asText()).isEqualTo("document");
        assertThat(payload.path("docName").asText()).isEqualTo("orcamento.pdf");
        assertThat(payload.path("mimetype").asText()).isEqualTo("application/pdf");
        assertThat(payload.path("text").asText()).isEqualTo("Confira");
    }

    @Test
    void audioComumUsaTipoAudioEConservaLegenda() {
        JsonNode payload = enviarMidia(TipoMensagem.AUDIO, "audio/mp4", "Explicacao");

        assertThat(payload.path("type").asText()).isEqualTo("audio");
        assertThat(payload.path("text").asText()).isEqualTo("Explicacao");
    }

    @Test
    void audioOggOpusUsaTipoNotaDeVoz() {
        JsonNode payload = enviarMidia(TipoMensagem.AUDIO, "audio/ogg; codecs=opus", null);

        assertThat(payload.path("type").asText()).isEqualTo("ptt");
    }

    @Test
    void templateERecusadoSemChamadaHttp() {
        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                NUMERO,
                ConteudoDeEnvio.MensagemTemplate.de("boas_vindas", "pt_BR"),
                UUID.randomUUID()));

        servidor.verify();
        assertThat(resultado).isEqualTo(
                ResultadoDeEnvio.Recusado.permanente("uazapi nao gerencia templates"));
    }

    @Test
    void localizacaoMantemAExcecaoDoDominioDeMidia() {
        assertThatThrownBy(() -> UazApiAdapter.tipoDaUazApi(TipoMensagem.LOCALIZACAO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LOCALIZACAO nao e midia transferida");
    }

    @Test
    void recebimentoContinuaExplicitamenteBloqueado() {
        assertThatThrownBy(() -> adapter.baixarMidiaRecebida("message-id"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("recebimento uazapi depende do levantamento do E148b");
    }

    @Test
    void erroDeValidacaoERecusaPermanente() {
        servidor.expect(once(), requestTo(URL_BASE + "/send/text"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"numero invalido\"}"));

        ResultadoDeEnvio resultado = enviarTexto("falha");

        servidor.verify();
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        ResultadoDeEnvio.Recusado recusado = (ResultadoDeEnvio.Recusado) resultado;
        assertThat(recusado.permanente()).isTrue();
        assertThat(recusado.motivo()).isEqualTo("HTTP 400 numero invalido");
    }

    @Test
    void erroDoProvedorERecusaTemporaria() {
        servidor.expect(once(), requestTo(URL_BASE + "/send/text"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"upstream\"}}"));

        ResultadoDeEnvio resultado = enviarTexto("tente novamente");

        servidor.verify();
        assertThat(resultado).isEqualTo(
                ResultadoDeEnvio.Recusado.temporario("HTTP 502 upstream"));
    }

    @Test
    void falhaDeConexaoERecusaTemporaria() {
        servidor.expect(once(), requestTo(URL_BASE + "/send/text"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused");
                });

        ResultadoDeEnvio resultado = enviarTexto("tente depois");

        servidor.verify();
        assertThat(resultado).isEqualTo(
                ResultadoDeEnvio.Recusado.temporario("provedor indisponivel: ResourceAccessException"));
    }

    @Test
    void autenticacaoAceitaStatusDaInstancia() {
        servidor.expect(once(), requestTo(URL_BASE + "/instance/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("token", "token-de-teste"))
                .andRespond(withSuccess("{\"status\":\"connected\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.verificarAutenticacao().autenticada()).isTrue();
        servidor.verify();
    }

    @Test
    void autenticacao401NaoVazaToken() {
        servidor.expect(once(), requestTo(URL_BASE + "/instance/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"token invalido\"}"));

        CanalGateway.AutenticacaoDoCanal resultado = adapter.verificarAutenticacao();

        servidor.verify();
        assertThat(resultado.autenticada()).isFalse();
        assertThat(resultado.detalhe()).isEqualTo("provedor recusou a credencial com HTTP 401");
        assertThat(resultado.detalhe()).doesNotContain("token-de-teste");
    }

    private JsonNode enviarMidia(TipoMensagem tipo, String mimetype, String legenda) {
        return enviarMidiaComMetadados(
                tipo,
                "{\"nome\":\"anexo\",\"mimetype\":\"" + mimetype + "\"}",
                legenda);
    }

    private JsonNode enviarMidiaComMetadados(TipoMensagem tipo, String metadados, String legenda) {
        final JsonNode[] payload = new JsonNode[1];
        servidor.expect(once(), requestTo(URL_BASE + "/send/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("token", "token-de-teste"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> payload[0] = json.readTree(
                        ((MockClientHttpRequest) request).getBodyAsBytes()))
                .andRespond(withSuccess("{\"messageid\":\"uaz-media-1\"}", MediaType.APPLICATION_JSON));

        ResultadoDeEnvio resultado = adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                NUMERO,
                new ConteudoDeEnvio.MensagemMidia(tipo, REFERENCIA, metadados, legenda),
                UUID.randomUUID()));

        servidor.verify();
        assertThat(resultado).isEqualTo(new ResultadoDeEnvio.Aceito("uaz-media-1"));
        return payload[0];
    }

    private ResultadoDeEnvio enviarTexto(String texto) {
        return adapter.enviar(new CanalGateway.Envio(
                UUID.randomUUID(),
                NUMERO,
                new ConteudoDeEnvio.MensagemLivre(texto),
                UUID.randomUUID()));
    }
}
