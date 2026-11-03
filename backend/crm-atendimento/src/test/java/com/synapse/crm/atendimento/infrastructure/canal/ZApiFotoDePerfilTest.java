package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ZApiFotoDePerfilTest {
    private final OkHttpClient http=mock(OkHttpClient.class);
    private final java.util.List<Request> pedidos=new java.util.ArrayList<>();
    static ZApiFotoProperties propriedades(boolean ativo) {
        return new ZApiFotoProperties(ativo,Duration.ofHours(6),Duration.ofHours(1),Duration.ofMinutes(15),
                Duration.ofMinutes(2),Duration.ofSeconds(10),1,4096,24000000,List.of("pps.whatsapp.net"));
    }
    private ZApiFotoDePerfil adapter(boolean ativo) {
        return new ZApiFotoDePerfil(new ZApiProperties(true,"instancia","token-sintetico","conta-sintetica",
                UUID.randomUUID(),Duration.ofSeconds(10),4096,1),propriedades(ativo),new ObjectMapper(),
                CircuitBreakerRegistry.ofDefaults(),http,"https://api.z-api.io");
    }
    static byte[] png() throws Exception {
        var saida=new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",saida);
        return saida.toByteArray();
    }
    private void responder(int consulta, String json, int download, byte[] bytes) throws Exception {
        when(http.newCall(any())).thenAnswer(chamada -> {
            Request pedido=chamada.getArgument(0); pedidos.add(pedido); var call=mock(Call.class);
            boolean api=pedido.url().host().equals("api.z-api.io");
            when(call.execute()).thenReturn(new Response.Builder().request(pedido).protocol(Protocol.HTTP_1_1)
                    .code(api?consulta:download).message("fixture")
                    .body(ResponseBody.create(api?json.getBytes(java.nio.charset.StandardCharsets.UTF_8):bytes,
                            MediaType.get(api?"application/json":"image/png"))).build());
            return call;
        });
    }
    @Test void consultaContatoExatoEDownloadSemCredenciais() throws Exception {
        responder(200,"{\"link\":\"https://pps.whatsapp.net/foto\"}",200,png());
        assertThat(adapter(true).buscar("5511999991111").orElseThrow().conteudo()).isEqualTo(png());
        assertThat(pedidos).hasSize(2);
        assertThat(pedidos.getFirst().url().encodedPath()).isEqualTo("/instances/instancia/token/token-sintetico/profile-picture");
        assertThat(pedidos.getFirst().url().queryParameter("phone")).isEqualTo("5511999991111");
        assertThat(pedidos.getFirst().header("Client-Token")).isEqualTo("conta-sintetica");
        assertThat(pedidos.getLast().headers().size()).isZero();
    }
    @Test void desligadoNaoConsulta() { assertThat(adapter(false).buscar("5511999991111")).isEmpty(); verifyNoInteractions(http); }
    @ParameterizedTest @ValueSource(strings={"null","\"\""})
    void semFotoFornecidaNaoBaixa(String link) throws Exception {
        responder(200,"{\"link\":"+link+"}",200,png());
        assertThat(adapter(true).buscar("5511999991111")).isEmpty(); assertThat(pedidos).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"http://pps.whatsapp.net/a","https://127.0.0.1/a","https://pps.whatsapp.net.evil/a",
            "https://user:pass@pps.whatsapp.net/a","https://pps.whatsapp.net:444/a","https://pps.whatsapp.net/a#x","file:///a"})
    void destinoInseguroNaoEConsultado(String link) throws Exception {
        responder(200,"{\"link\":\""+link+"\"}",200,png());
        assertThatThrownBy(()->adapter(true).buscar("5511999991111")).hasMessageNotContaining(link);
        assertThat(pedidos).hasSize(1);
    }
    @ParameterizedTest @ValueSource(ints={401,403,429,500,503})
    void falhasDaConsultaNaoSaoAusencia(int status) throws Exception {
        responder(status,"{\"link\":null}",200,png());
        assertThatThrownBy(()->adapter(true).buscar("5511999991111")).isInstanceOf(RuntimeException.class);
        assertThat(pedidos).hasSize(1);
    }
    @ParameterizedTest @ValueSource(ints={301,302,307,401,403,429,500})
    void falhaOuRedirectDoDownloadNaoSegueNemViraAusencia(int status) throws Exception {
        responder(200,"{\"link\":\"https://pps.whatsapp.net/foto\"}",status,png());
        assertThatThrownBy(()->adapter(true).buscar("5511999991111")).isInstanceOf(RuntimeException.class);
        assertThat(pedidos).hasSize(2);
    }
    @ParameterizedTest @ValueSource(strings={"nao-imagem","excesso","pixels","corrompida"})
    void recusaConteudoInvalido(String caso) throws Exception {
        byte[] bytes=switch(caso) {
            case "excesso" -> new byte[4097];
            case "pixels" -> { byte[] p=png(); p[16]=0x7f; yield p; }
            case "corrompida" -> java.util.Arrays.copyOf(png(),12);
            default -> "conteudo falso".getBytes();
        };
        responder(200,"{\"link\":\"https://pps.whatsapp.net/foto\"}",200,bytes);
        assertThatThrownBy(()->adapter(true).buscar("5511999991111")).isInstanceOf(RuntimeException.class);
    }
    @Test void timeoutNaoVazaTokenOuTelefone() throws Exception {
        var call=mock(Call.class); when(http.newCall(any())).thenReturn(call);
        when(call.execute()).thenThrow(new java.net.SocketTimeoutException("5511999991111 token-sintetico"));
        assertThatThrownBy(()->adapter(true).buscar("5511999991111")).hasMessageNotContaining("5511999991111")
                .hasMessageNotContaining("token-sintetico").hasNoCause();
    }
    @Test void naoTransformaLidEmTelefone() {
        assertThatThrownBy(()->adapter(true).buscar("123456789@lid")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(http);
    }
}
