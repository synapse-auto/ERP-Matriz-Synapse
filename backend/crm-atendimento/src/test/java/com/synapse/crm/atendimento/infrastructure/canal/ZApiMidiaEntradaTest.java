package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.*;
import org.apache.tika.Tika;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.synapse.crm.atendimento.domain.canal.*;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

class ZApiMidiaEntradaTest {
    private final OkHttpClient http = mock(OkHttpClient.class);
    private final Call chamada = mock(Call.class);
    private final LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
    private ZApiMidiaEntrada entrada;
    private static final String URL = "https://cdn.exemplo.test/arquivo?assinatura=segredo-nao-logar";
    @BeforeEach void preparar() {
        when(limites.limiteEmBytes(any())).thenReturn(Optional.of(1024L * 1024));
        when(http.newCall(any())).thenReturn(chamada);
        entrada = new ZApiMidiaEntrada(http, new ZApiMidiaEntradaProperties(Set.of("cdn.exemplo.test"), 1), limites,
                bytes -> new Tika().detect(bytes), CircuitBreakerRegistry.ofDefaults());
    }
    static Stream<Arguments> arquivos() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=");
        var saida = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", saida);
        return Stream.of(Arguments.of(TipoMensagem.IMAGEM, png, "image/png"),
                Arguments.of(TipoMensagem.DOCUMENTO, png, "image/png"),
                Arguments.of(TipoMensagem.DOCUMENTO, saida.toByteArray(), "image/jpeg"),
                Arguments.of(TipoMensagem.DOCUMENTO, fixture("documento-sintetico.pdf"), "application/pdf"),
                Arguments.of(TipoMensagem.AUDIO, fixture("audio-sintetico.ogg"), "audio/ogg"));
    }
    private static byte[] fixture(String nome) throws java.io.IOException {
        try(var f=ZApiMidiaEntradaTest.class.getResourceAsStream("/midia/"+nome)){return f.readAllBytes();}
    }
    @ParameterizedTest @MethodSource("arquivos")
    void baixaReferenciaDocumentadaPreservaBytesEMimeRealSemCredenciais(TipoMensagem tipo, byte[] bytes, String mime) throws Exception {
        if(tipo==TipoMensagem.AUDIO) assertThat(new Tika().detect(bytes)).isEqualTo("application/ogg");
        responder(200, bytes);
        try (var midia = entrada.abrir(URL, tipo)) {
            assertThat(midia.conteudo().readAllBytes()).isEqualTo(bytes);
            assertThat(midia.tamanho()).isEqualTo(bytes.length);
            assertThat(midia.mimetype()).isEqualTo(mime);
        }
        var pedido = org.mockito.ArgumentCaptor.forClass(Request.class); verify(http).newCall(pedido.capture());
        assertThat(pedido.getValue().method()).isEqualTo("GET");
        assertThat(pedido.getValue().headers().size()).isZero();
        assertThat(pedido.getValue().url().toString()).isEqualTo(URL);
    }
    @ParameterizedTest @ValueSource(strings={"http://cdn.exemplo.test/a", "https://127.0.0.1/a", "https://cdn.exemplo.test.evil/a", "https://user:pass@cdn.exemplo.test/a", "https://cdn.exemplo.test:444/a", "https://cdn.exemplo.test/a#fragmento", "file:///a"})
    void recusaDestinoAntesDoTransporte(String url) {
        assertThatThrownBy(() -> entrada.abrir(url, TipoMensagem.DOCUMENTO)).isInstanceOf(MidiaRecebidaInvalidaException.class);
        verifyNoInteractions(http);
    }
    @ParameterizedTest @ValueSource(ints={301,302,307,400,401,403,404})
    void naoSegueRedirectNemReconsultaUrlComErroPermanente(int status) throws Exception {
        responder(status, new byte[]{1});
        assertThatThrownBy(() -> entrada.abrir(URL, TipoMensagem.DOCUMENTO)).isInstanceOf(MidiaRecebidaInvalidaException.class);
        verify(http, times(1)).newCall(any());
    }
    @ParameterizedTest @ValueSource(ints={408,429,500,503})
    void erroTemporarioPermaneceRecuperavelSemUrl(int status) throws Exception {
        responder(status, new byte[]{1});
        assertThatThrownBy(() -> entrada.abrir(URL, TipoMensagem.DOCUMENTO))
                .isInstanceOf(MidiaRecebidaTemporariamenteIndisponivelException.class).hasMessageNotContaining("segredo-nao-logar");
    }
    @Test void rejeitaConteudoDisfarcadoEVazioETamanhoExcedido() throws Exception {
        for (byte[] bytes : new byte[][] {new byte[0], "<html>nao e PDF</html>".getBytes(StandardCharsets.UTF_8), new byte[1024 * 1024 + 1]}) {
            responder(200, bytes);
            assertThatThrownBy(() -> entrada.abrir(URL, TipoMensagem.DOCUMENTO)).isInstanceOf(MidiaRecebidaInvalidaException.class);
        }
    }
    @Test void transporteRealFixaDnsSemProxyRedirectRetryOuCredenciais() {
        var cliente=ZApiMidiaEntrada.cliente(Duration.ofSeconds(2));
        assertThat(cliente.dns()).isInstanceOf(ZApiDnsPublico.class);
        assertThat(cliente.proxy()).isEqualTo(java.net.Proxy.NO_PROXY);
        assertThat(cliente.followRedirects()).isFalse();assertThat(cliente.followSslRedirects()).isFalse();
        assertThat(cliente.retryOnConnectionFailure()).isFalse();assertThat(cliente.interceptors()).isEmpty();
        assertThat(cliente.callTimeoutMillis()).isEqualTo(2000);
    }
    @Test void oggTruncadoOuSemOpusNaoViraAudioPermitido() throws Exception {
        try(var f=getClass().getResourceAsStream("/midia/audio-sintetico.ogg")) {
            byte[] completo=f.readAllBytes();
            responder(200,java.util.Arrays.copyOf(completo,completo.length-1));
            assertThatThrownBy(()->entrada.abrir(URL,TipoMensagem.AUDIO)).isInstanceOf(MidiaRecebidaInvalidaException.class);
        }
    }
    @Test void videoUsaTrilhaRealENaoMudaParaAudio() throws Exception {
        try (var arquivo = getClass().getResourceAsStream("/midia/video-validacao.mp4")) {
            assertThat(arquivo).isNotNull();
            byte[] bytes = arquivo.readAllBytes(); responder(200, bytes);
            try (var midia = entrada.abrir(URL, TipoMensagem.VIDEO)) {
                assertThat(midia.mimetype()).isEqualTo("video/mp4");
                assertThat(midia.conteudo().readAllBytes()).isEqualTo(bytes);
            }
            responder(200, bytes);
            assertThatThrownBy(() -> entrada.abrir(URL, TipoMensagem.AUDIO)).isInstanceOf(MidiaRecebidaInvalidaException.class);
        }
    }
    private void responder(int status, byte[] bytes) throws Exception {
        when(chamada.execute()).thenReturn(new Response.Builder().request(new Request.Builder().url(URL).build())
                .protocol(Protocol.HTTP_1_1).code(status).message("teste")
                .body(ResponseBody.create(bytes, MediaType.get("application/pdf"))).build());
    }
}
