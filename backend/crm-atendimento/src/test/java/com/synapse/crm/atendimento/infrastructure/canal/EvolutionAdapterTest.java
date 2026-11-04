package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.synapse.crm.atendimento.domain.canal.*;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.*;

class EvolutionAdapterTest {
    OkHttpClient http;Call call;EvolutionAdapter adapter;ArmazenamentoDeMidia storage;DetectorDeTipoReal detector;
    LimiteDeAnexoRepositorio limites;List<Request> pedidos;
    final ObjectMapper json=new ObjectMapper();
    @BeforeEach void preparar()throws Exception {
        http=mock(OkHttpClient.class);call=mock(Call.class);storage=mock(ArmazenamentoDeMidia.class);detector=mock(DetectorDeTipoReal.class);limites=mock(LimiteDeAnexoRepositorio.class);pedidos=new ArrayList<>();
        when(limites.limiteEmBytes(any())).thenReturn(Optional.empty());
        when(http.newCall(any())).thenAnswer(i->{pedidos.add(i.getArgument(0));return call;});
        adapter=new EvolutionAdapter(EvolutionWebhookTest.config(),json,CircuitBreakerRegistry.ofDefaults(),storage,limites,detector,http);
    }
    Response resposta(int status,String body){return new Response.Builder().request(new Request.Builder().url("https://evolution.example.test").build()).protocol(Protocol.HTTP_1_1).code(status).message("simulado").body(ResponseBody.create(body,MediaType.get("application/json"))).build();}
    CanalGateway.Envio texto(){return new CanalGateway.Envio(UUID.randomUUID(),"5561999990000",new ConteudoDeEnvio.MensagemLivre("Sintetico"),EvolutionWebhookTest.CREDENCIAL);}
    @Test void textoUsaContrato237EAceiteNaoAfirmaEntrega()throws Exception {
        when(call.execute()).thenReturn(resposta(201,"{\"key\":{\"id\":\"wa-1\",\"fromMe\":true},\"status\":\"PENDING\"}"));
        assertThat(adapter.enviar(texto())).isEqualTo(new ResultadoDeEnvio.Enfileirado("evo-teste",null,"wa-1"));
        var r=pedidos.getFirst();assertThat(r.url().encodedPath()).isEqualTo("/message/sendText/evo-teste");assertThat(r.header("apikey")).isEqualTo("chave-sintetica");
        var b=new Buffer();r.body().writeTo(b);assertThat(json.readTree(b.readUtf8()).path("text").asText()).isEqualTo("Sintetico");
    }
    @ParameterizedTest @ValueSource(ints={200,400,429,500})
    void respostaIncompletaOuFalhaPodeTerEfeitoSemRetry(int status)throws Exception {
        when(call.execute()).thenReturn(resposta(status,"{}"));assertThat(adapter.enviar(texto())).isInstanceOf(ResultadoDeEnvio.Ambiguo.class);verify(call,times(1)).execute();
    }
    @Test void timeoutNaoRedisparaECredencialDivergenteNaoChamaHttp()throws Exception {
        var original=texto();assertThat(adapter.enviar(new CanalGateway.Envio(original.mensagemId(),original.telefoneDestino(),original.conteudo(),UUID.randomUUID()))).isInstanceOf(ResultadoDeEnvio.Recusado.class);verifyNoInteractions(http);
        when(call.execute()).thenThrow(new IOException("segredo-nao-expor"));assertThat(adapter.enviar(original)).isEqualTo(new ResultadoDeEnvio.Ambiguo("EVOLUTION_ENVIO_NAO_CONFIRMADO"));verify(call,times(1)).execute();
    }
    @Test void referenciaOutraInstanciaNaoEnviaENaoDescartaCitacao() {
        var t=texto();var quote=new IdentidadeDeMensagemDoCanal("evolution","outra","WHATSAPP","wa");
        assertThat(adapter.enviar(new CanalGateway.Envio(t.mensagemId(),t.telefoneDestino(),t.conteudo(),t.credencialId(),"wa",quote))).isInstanceOf(ResultadoDeEnvio.Recusado.class);verifyNoInteractions(http);
    }
    @ParameterizedTest @ValueSource(strings={"IMAGEM","AUDIO","VIDEO","DOCUMENTO"})
    void midiaUsaBase64EmFluxoComNomeEMime(String tipo)throws Exception {
        String mime=switch(tipo){case "IMAGEM"->"image/png";case "AUDIO"->"audio/mpeg";case "VIDEO"->"video/mp4";default->"application/vnd.openxmlformats-officedocument.wordprocessingml.document";};
        byte[] bytes={1,2,3,4};when(storage.abrirLeitura("privado")).thenReturn(new java.io.ByteArrayInputStream(bytes));
        when(call.execute()).thenAnswer(i->{var b=new Buffer();pedidos.getLast().body().writeTo(b);var n=json.readTree(b.readUtf8());assertThat(Base64.getDecoder().decode(n.path("media").asText())).containsExactly(bytes);assertThat(n.path("mimetype").asText()).isEqualTo(mime);return resposta(201,"{\"key\":{\"id\":\"w1\",\"fromMe\":true}}");});
        String metadados=json.createObjectNode().put("nome","Relatorio com acento.docx").put("mimetype",mime).put("tamanho",4).toString();
        assertThat(adapter.enviar(new CanalGateway.Envio(UUID.randomUUID(),"5561999990000",new ConteudoDeEnvio.MensagemMidia(TipoMensagem.valueOf(tipo),"privado",metadados,null),EvolutionWebhookTest.CREDENCIAL))).isInstanceOf(ResultadoDeEnvio.Enfileirado.class);
    }
    @Test void midiaRecebidaResolveChaveSemUrlExternaEDecodificaBytes()throws Exception {
        when(call.execute()).thenReturn(resposta(200,"{\"mimetype\":\"image/png\",\"base64\":\"AQIDBA==\"}"));when(detector.detectar(any(java.nio.file.Path.class))).thenReturn("image/png");
        String ref="{\"id\":\"w1\",\"remoteJid\":\"5561999990000@s.whatsapp.net\",\"fromMe\":false}";
        try(var fluxo=adapter.abrirMidiaRecebida(ref,TipoMensagem.IMAGEM).orElseThrow()) {assertThat(fluxo.conteudo().readAllBytes()).containsExactly(1,2,3,4);}
        assertThat(pedidos.getFirst().url().encodedPath()).isEqualTo("/chat/getBase64FromMediaMessage/evo-teste");
        assertThatThrownBy(()->adapter.baixarMidiaRecebida("https://127.0.0.1/segredo")).isInstanceOf(MidiaRecebidaInvalidaException.class);
        verify(call,times(1)).execute();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void citacaoResolvidaPorIdEConversaAntesDeDespachar(boolean enviada)throws Exception {
        when(call.execute()).thenReturn(resposta(200,"{\"messages\":{\"records\":[{\"key\":{\"id\":\"wa-original\",\"fromMe\":"+enviada+",\"remoteJid\":\"5561999990000@s.whatsapp.net\"},\"message\":{\"conversation\":\"Original sintetico\"}}]}}"),resposta(201,"{\"key\":{\"id\":\"wa-nova\",\"fromMe\":true}}"));
        var t=texto();var id=new IdentidadeDeMensagemDoCanal("evolution","evo-teste","WHATSAPP","wa-original");
        assertThat(adapter.enviar(new CanalGateway.Envio(t.mensagemId(),t.telefoneDestino(),t.conteudo(),t.credencialId(),id.valor(),id))).isInstanceOf(ResultadoDeEnvio.Enfileirado.class);
        var b=new Buffer();pedidos.getLast().body().writeTo(b);assertThat(json.readTree(b.readUtf8()).path("quoted").path("key").path("id").asText()).isEqualTo("wa-original");
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"base64\":\"!nao-base64!\"}","{\"base64\":\"\"}"})
    void midiaSemBytesValidosNaoViraArquivoDisponivel(String body)throws Exception {
        when(call.execute()).thenReturn(resposta(200,body));
        assertThatThrownBy(()->adapter.abrirMidiaRecebida("{\"id\":\"w1\",\"remoteJid\":\"5561999990000@s.whatsapp.net\",\"fromMe\":false}",TipoMensagem.IMAGEM))
                .isInstanceOf(MidiaRecebidaInvalidaException.class);
        verifyNoInteractions(storage);verify(call,times(1)).execute();
    }
    @ParameterizedTest @ValueSource(ints={410,500})
    void falhaHttpDaMidiaPreservaClassificacaoSemRetry(int status)throws Exception {
        when(call.execute()).thenReturn(resposta(status,"{\"segredo\":\"nao-expor\"}"));
        assertThatThrownBy(()->adapter.abrirMidiaRecebida("{\"id\":\"w1\",\"remoteJid\":\"5561999990000@s.whatsapp.net\",\"fromMe\":false}",TipoMensagem.AUDIO))
                .isInstanceOf(status==410?MidiaRecebidaRemovidaNoProvedorException.class:MidiaRecebidaTemporariamenteIndisponivelException.class)
                .hasMessageNotContaining("nao-expor");
        verifyNoInteractions(storage);verify(call,times(1)).execute();
    }
}
