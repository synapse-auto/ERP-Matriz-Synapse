package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

class ZApiMidiaSaidaTest {
    private static final UUID CREDENCIAL = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BASE = "https://api.z-api.io/instances/instancia-teste/token/segredo-instancia/";
    private final ObjectMapper json = new ObjectMapper();
    private final ArmazenamentoDeMidia storage = mock(ArmazenamentoDeMidia.class);
    private final LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
    private MockRestServiceServer servidor;
    private ZApiAdapter adapter;
    private CircuitBreakerRegistry breakers;

    @BeforeEach
    void preparar() {
        RestClient.Builder cliente = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(cliente).build();
        breakers = CircuitBreakerRegistry.ofDefaults();
        when(limites.limiteEmBytes(any())).thenReturn(Optional.empty());
        adapter = new ZApiAdapter(cliente,
                new ZApiProperties(true, "instancia-teste", "segredo-instancia", "segredo-cliente", CREDENCIAL,
                        Duration.ofSeconds(10), 4096, 1), json, breakers, "instancia-teste",
                new ZApiMidiaSaida(storage, limites, json));
    }

    static Stream<Arguments> midias() {
        return Stream.of(
                Arguments.of(TipoMensagem.DOCUMENTO, "application/msword", "Relatório final.DOC", "send-document/doc", "document"),
                Arguments.of(TipoMensagem.DOCUMENTO, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Relatório final.DOCX", "send-document/docx", "document"),
                Arguments.of(TipoMensagem.IMAGEM, "image/png", "imagem.png", "send-image", "image"),
                Arguments.of(TipoMensagem.AUDIO, "audio/ogg", "gravacao.ogg", "send-audio", "audio"),
                Arguments.of(TipoMensagem.VIDEO, "video/mp4", "video.mp4", "send-video", "video"),
                Arguments.of(TipoMensagem.DOCUMENTO, "application/pdf", "proposta.pdf", "send-document/pdf", "document"),
                Arguments.of(TipoMensagem.DOCUMENTO, "image/png", "foto.png", "send-document/png", "document"),
                Arguments.of(TipoMensagem.DOCUMENTO, "image/jpeg", "foto.jpg", "send-document/jpg", "document"),
                Arguments.of(TipoMensagem.DOCUMENTO, "image/jpeg", "Proposta clinica.jpeg", "send-document/jpg", "document"),
                Arguments.of(TipoMensagem.DOCUMENTO, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "planilha.xlsx", "send-document/xlsx", "document"));
    }

    @ParameterizedTest @MethodSource("midias")
    void citacaoDeMidiaUsaCampoDocumentadoOuRecusaAudioSemContrato(TipoMensagem tipo,String mime,String nome,String rota,String campo) throws Exception {
        var original=envio(tipo,mime,nome,3,null);
        var identidade=new com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal("z-api","instancia-teste","WHATSAPP","original");
        var citada=new CanalGateway.Envio(original.mensagemId(),original.telefoneDestino(),original.conteudo(),CREDENCIAL,"original",identidade);
        if(tipo==TipoMensagem.AUDIO) {
            assertThat(adapter.enviar(citada)).isEqualTo(ResultadoDeEnvio.Recusado.permanente("CITACAO_NAO_SUPORTADA_PELO_CANAL"));
            verifyNoInteractions(storage);return;
        }
        when(storage.abrirLeitura(any())).thenReturn(new ByteArrayInputStream(new byte[]{1,2,3}));
        servidor.expect(requestTo(BASE+rota)).andExpect(jsonPath("$.messageId").value("original"))
                .andRespond(withSuccess("{\"zaapId\":\"processo\",\"messageId\":\"resposta\"}",MediaType.APPLICATION_JSON));
        assertThat(adapter.enviar(citada)).isInstanceOf(ResultadoDeEnvio.Enfileirado.class);
        servidor.verify();
    }

    @ParameterizedTest
    @MethodSource("midias")
    void transmiteBytesDoStorageSemUrlPublicaPreservandoTipoNomeELegenda(
            TipoMensagem tipo, String mime, String nome, String rota, String campo) throws Exception {
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, (byte) 255, (byte) 254};
        if (mime.equals("application/msword") || mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            try (var recurso = getClass().getResourceAsStream(mime.equals("application/msword") ? "/word/vazio.doc" : "/word/sintetico.docx")) { bytes = recurso.readAllBytes(); }
        }
        final byte[] esperado = bytes;
        when(storage.abrirLeitura("objeto-privado")).thenReturn(new ByteArrayInputStream(bytes));
        String legenda = tipo == TipoMensagem.AUDIO ? null : "Legenda 🧪\ncom \"aspas\"";
        servidor.expect(requestTo(BASE + rota)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Client-Token", "segredo-cliente"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(pedido -> {
                    var corpo = json.readTree(((MockClientHttpRequest) pedido).getBodyAsBytes());
                    assertThat(corpo.path("phone").asText()).isEqualTo("5561999999999");
                    String dado = corpo.path(campo).asText();
                    assertThat(dado).startsWith("data:" + mime + ";base64,");
                    assertThat(Base64.getDecoder().decode(dado.substring(dado.indexOf(',') + 1))).isEqualTo(esperado);
                    if (tipo == TipoMensagem.DOCUMENTO) assertThat(corpo.path("fileName").asText()).isEqualTo(nome);
                    if (tipo == TipoMensagem.AUDIO) assertThat(corpo.has("caption")).isFalse();
                    else assertThat(corpo.path("caption").asText()).isEqualTo(legenda);
                }).andRespond(withSuccess("{\"zaapId\":\"processo\",\"messageId\":\"mensagem\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.enviar(envio(tipo, mime, nome, bytes.length, legenda)))
                .isEqualTo(new ResultadoDeEnvio.Enfileirado("instancia-teste", "processo", "mensagem"));
        verify(storage).abrirLeitura("objeto-privado");
        verify(storage, never()).baixar(any());
        verify(storage, never()).urlAssinada(any(), any());
        servidor.verify();
    }

    @Test
    void falhaDoStorageAntesDoPostPermiteRecuperacaoSemVazarReferencia() throws Exception {
        when(storage.abrirLeitura(any())).thenThrow(new IllegalStateException("objeto-privado https://segredo"));
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", 10, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.temporario("Z_API_STORAGE_INDISPONIVEL"));
        servidor.verify();
    }

    @Test
    void timeoutDepoisDeIniciarPostNuncaViraRetryTemporario() throws Exception {
        when(storage.abrirLeitura(any())).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        servidor.expect(requestTo(BASE + "send-document/pdf"))
                .andRespond(withException(new IOException("segredo-instancia")));
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", 1, null)))
                .isEqualTo(new ResultadoDeEnvio.Ambiguo("Z_API_RESPOSTA_NAO_CONFIRMADA"));
        servidor.verify();
    }

    @Test
    void limiteConfiguradoEConferidoNosBytesMesmoComMetadadoFalso() throws Exception {
        when(limites.limiteEmBytes(any())).thenReturn(Optional.of(3L));
        when(storage.abrirLeitura(any())).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", 3, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA"));
        servidor.verify();
    }

    @Test
    void bytesDivergentesDoTamanhoPersistidoNaoSaoDespachados() throws Exception {
        when(storage.abrirLeitura(any())).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", 2, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA"));
        servidor.verify();
    }

    @Test void gravacaoDoComposerCorrompidaNoStorageNaoIniciaPost() throws Exception {
        try (var arquivo = getClass().getResourceAsStream("/midia/audio-sintetico.ogg")) {
            byte[] completo = arquivo.readAllBytes();
            byte[] truncado = java.util.Arrays.copyOf(completo, completo.length - 1);
            when(storage.abrirLeitura(any())).thenReturn(new ByteArrayInputStream(truncado));
            var midia = (ConteudoDeEnvio.MensagemMidia) envio(TipoMensagem.AUDIO, "audio/ogg", "gravacao.ogg", truncado.length, null).conteudo();
            var metadados = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(midia.metadados());
            metadados.put("gravacaoDoComposer", true);
            var pedido = new CanalGateway.Envio(UUID.randomUUID(), "5561999999999",
                    new ConteudoDeEnvio.MensagemMidia(TipoMensagem.AUDIO, midia.referenciaStorage(), metadados.toString(), null), CREDENCIAL);
            assertThat(adapter.enviar(pedido)).isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA"));
            servidor.verify();
        }
    }

    @Test void oggGenericoSoEPermitidoDepoisDeValidarContainerOpusCompleto() throws Exception {
        try (var arquivo = getClass().getResourceAsStream("/midia/audio-sintetico.ogg")) {
            byte[] completo = arquivo.readAllBytes();
            var tipos = com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos.classificar("application/ogg", completo);
            assertThat(tipos).contains(new com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos.Classificacao(TipoMensagem.AUDIO,"audio/ogg"));
            assertThat(com.synapse.crm.atendimento.domain.midia.TiposDeMidiaPermitidos.classificar("application/ogg",
                    java.util.Arrays.copyOf(completo, completo.length - 1))).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/zip", "application/x-msdownload", "image/png;evil=1", "", "application/octet-stream"})
    void mimeNaoComprovadoNaoAbreStorageNemRede(String mime) throws Exception {
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, mime, "falso.pdf", 1, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA"));
        verifyNoInteractions(storage);
        servidor.verify();
    }

    @Test
    void breakerAbertoNaoAbreStorageENemArquivoTemporario() throws Exception {
        breakers.circuitBreaker("canal-z-api").transitionToOpenState();
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", 1, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.temporario("Z_API_DISJUNTOR_ABERTO"));
        verifyNoInteractions(storage, limites);
        servidor.verify();
    }

    @Test
    void audioComLegendaNaoAPerdeSilenciosamente() throws Exception {
        assertThat(adapter.enviar(envio(TipoMensagem.AUDIO, "audio/ogg", "audio.ogg", 1, "legenda")))
                .isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA"));
        verifyNoInteractions(storage);
    }

    @Test
    void preparaUmaVezFechaStreamELimpaArquivo() throws Exception {
        var entrada = spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(storage.abrirLeitura(any())).thenReturn(entrada);
        var envio = envio(TipoMensagem.DOCUMENTO, "application/pdf", "../../proposta.exe", 3, null);
        var preparador = new ZApiMidiaSaida(storage, limites, json);
        java.nio.file.Path temporario;
        try (var preparada = preparador.preparar((ConteudoDeEnvio.MensagemMidia) envio.conteudo())) {
            temporario = preparada.arquivo();
            assertThat(preparada.nome()).isEqualTo("proposta.pdf");
            assertThat(Files.readAllBytes(temporario)).containsExactly(1, 2, 3);
            verify(entrada).close();
        }
        assertThat(temporario).doesNotExist();
    }

    @Test
    void limitePersistidoInvalidoReprovaAntesDeAbrirStorage() throws Exception {
        when(limites.limiteEmBytes(any())).thenReturn(Optional.of(0L));
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", 1, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA"));
        verifyNoInteractions(storage);
    }

    @Test
    void bufferMaiorPreservaPaddingBase64SemUsarBaixarIntegral() throws Exception {
        byte[] bytes = new byte[65539];
        new java.util.Random(7).nextBytes(bytes);
        when(storage.abrirLeitura(any())).thenReturn(new ByteArrayInputStream(bytes));
        servidor.expect(requestTo(BASE + "send-document/pdf")).andExpect(pedido -> {
            String dado = json.readTree(((MockClientHttpRequest) pedido).getBodyAsBytes()).path("document").asText();
            assertThat(Base64.getDecoder().decode(dado.substring(dado.indexOf(',') + 1))).isEqualTo(bytes);
        }).andRespond(withSuccess("{\"zaapId\":\"processo\",\"messageId\":\"mensagem\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", bytes.length, null)))
                .isInstanceOf(ResultadoDeEnvio.Enfileirado.class);
        verify(storage, never()).baixar(any());
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "invalido", "{\"mimetype\":\"application/pdf\",\"tamanho\":\"12\"}",
            "{\"mimetype\":\"application/pdf\",\"tamanho\":1.5}", "{\"mimetype\":\"application/pdf\",\"tamanho\":0}",
            "{\"mimetype\":\"application/pdf\",\"tamanho\":104857601}"})
    void metadadosAusentesIncoerentesOuAcimaDoTetoNaoDisparamIo(String metadados) {
        var envio = new CanalGateway.Envio(UUID.randomUUID(), "5561999999999",
                new ConteudoDeEnvio.MensagemMidia(TipoMensagem.DOCUMENTO, "objeto-privado", metadados, null), CREDENCIAL);
        assertThat(adapter.enviar(envio)).isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_MIDIA_INVALIDA"));
        verifyNoInteractions(storage);
        servidor.verify();
    }

    @Test
    void erroNaLeituraParcialFechaStreamENaoIniciaPost() throws Exception {
        var entrada = spy(new java.io.InputStream() {
            private boolean primeiro = true;
            @Override public int read() throws IOException {
                if (primeiro) { primeiro = false; return 1; }
                throw new IOException("chave-privada-token-storage");
            }
        });
        when(storage.abrirLeitura(any())).thenReturn(entrada);
        assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "arquivo.pdf", 20, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.temporario("Z_API_STORAGE_INDISPONIVEL"));
        verify(entrada).close();
        servidor.verify();
    }

    @Test
    void corpoBase64NomeELegendaNaoVazamNosLogsDebug() throws Exception {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level anterior = raiz.getLevel();
        ListAppender<ILoggingEvent> capturado = new ListAppender<>();
        capturado.start();
        raiz.addAppender(capturado);
        raiz.setLevel(Level.DEBUG);
        try {
            byte[] bytes = "conteudo-privado-planilha".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            when(storage.abrirLeitura(any())).thenReturn(new ByteArrayInputStream(bytes));
            servidor.expect(requestTo(BASE + "send-document/pdf"))
                    .andRespond(withSuccess("{\"zaapId\":\"processo\",\"messageId\":\"mensagem\"}", MediaType.APPLICATION_JSON));
            assertThat(adapter.enviar(envio(TipoMensagem.DOCUMENTO, "application/pdf", "nome-privado.pdf", bytes.length, "legenda-privada")))
                    .isInstanceOf(ResultadoDeEnvio.Enfileirado.class);
            assertThat(capturado.list.stream().map(ILoggingEvent::getFormattedMessage).toList().toString())
                    .doesNotContain("segredo-instancia", "segredo-cliente", "5561999999999", "api.z-api.io",
                            "objeto-privado", "nome-privado", "legenda-privada", Base64.getEncoder().encodeToString(bytes));
            servidor.verify();
        } finally {
            raiz.setLevel(anterior);
            raiz.detachAppender(capturado);
            capturado.stop();
        }
    }

    private CanalGateway.Envio envio(TipoMensagem tipo, String mime, String nome, int tamanho, String legenda) throws Exception {
        return new CanalGateway.Envio(UUID.randomUUID(), "5561999999999",
                new ConteudoDeEnvio.MensagemMidia(tipo, "objeto-privado",
                        json.writeValueAsString(Map.of("nome", nome, "mimetype", mime, "tamanho", tamanho)), legenda), CREDENCIAL);
    }
}
