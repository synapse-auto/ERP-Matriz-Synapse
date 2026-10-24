package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;

class ZApiAdapterTest {
    private static final UUID CREDENCIAL = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BASE = "https://api.z-api.io/instances/instancia-teste/token/segredo-instancia";
    private MockRestServiceServer servidor;
    private ZApiAdapter adapter;
    private CircuitBreakerRegistry breakers;

    @BeforeEach
    void preparar() {
        RestClient.Builder cliente = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(cliente).build();
        breakers = CircuitBreakerRegistry.ofDefaults();
        adapter = criar(cliente, propriedades(true, "segredo-instancia", CREDENCIAL));
    }

    @Test
    void textoEnfileiradoPreservaIdsDistintosSemAfirmarEnvioOuEntrega() {
        servidor.expect(requestTo(BASE + "/send-text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Client-Token", "segredo-cliente"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().json("{\"phone\":\"5561999999999\",\"message\":\"Olá\\nMundo\"}"))
                .andRespond(withSuccess("{\"zaapId\":\"z-1\",\"messageId\":\"w-1\",\"id\":\"w-1\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.enviar(envio(CREDENCIAL, null)))
                .isEqualTo(new ResultadoDeEnvio.Enfileirado("instancia-teste", "z-1", "w-1"));
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"zaapId\":\"z\"}", "{\"messageId\":\"w\"}", "{\"zaapId\":\"z\",\"messageId\":\"w\",\"id\":\"outro\"}", "<html>erro</html>", "{\"zaapId\":\"z\",\"messageId\":\"w\",\"error\":\"rejeitado\"}"})
    void respostaInvalidaAposPostFicaAmbiguaSemRetry(String corpo) {
        servidor.expect(requestTo(BASE + "/send-text")).andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        assertThat(adapter.enviar(envio(CREDENCIAL, null))).isInstanceOf(ResultadoDeEnvio.Ambiguo.class);
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 400, 401, 403, 404, 429, 500, 503})
    void erroHttpNaoRecebeRetryCegoNemExpoeCorpo(int status) {
        servidor.expect(requestTo(BASE + "/send-text"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("segredo-instancia segredo-cliente mensagem privada"));
        ResultadoDeEnvio resultado = adapter.enviar(envio(CREDENCIAL, null));
        assertThat(resultado).isInstanceOf(ResultadoDeEnvio.Ambiguo.class);
        assertThat(resultado.toString()).doesNotContain("segredo", "privada", "https:");
        servidor.verify();
    }

    @Test
    void timeoutAposPostNuncaViraRecusaTemporaria() {
        servidor.expect(requestTo(BASE + "/send-text"))
                .andRespond(withException(new IOException(BASE + "?token=segredo-cliente")));
        assertThat(adapter.enviar(envio(CREDENCIAL, null)))
                .isEqualTo(new ResultadoDeEnvio.Ambiguo("Z_API_RESPOSTA_NAO_CONFIRMADA"));
        servidor.verify();
    }

    @Test
    void corpoAcimaDoLimiteNaoViraAceite() {
        servidor.expect(requestTo(BASE + "/send-text"))
                .andRespond(withSuccess(" ".repeat(4097), MediaType.APPLICATION_JSON));
        assertThat(adapter.enviar(envio(CREDENCIAL, null))).isInstanceOf(ResultadoDeEnvio.Ambiguo.class);
        servidor.verify();
    }

    @Test
    void disjuntorAbertoAntesDaRedePodeReagendar() {
        breakers.circuitBreaker("canal-z-api").transitionToOpenState();
        assertThat(adapter.enviar(envio(CREDENCIAL, null)))
                .isEqualTo(ResultadoDeEnvio.Recusado.temporario("Z_API_DISJUNTOR_ABERTO"));
        servidor.verify();
    }

    @Test
    void desligadoOuSemConfiguracaoNaoFazHttp() {
        RestClient.Builder cliente = RestClient.builder();
        MockRestServiceServer semRede = MockRestServiceServer.bindTo(cliente).build();
        for (ZApiProperties props : new ZApiProperties[] {
                propriedades(false, "segredo-instancia", CREDENCIAL),
                propriedades(true, "", CREDENCIAL), propriedades(true, "segredo-instancia", null)}) {
            ZApiAdapter local = criar(cliente.clone(), props);
            assertThat(local.enviar(envio(CREDENCIAL, null))).isInstanceOf(ResultadoDeEnvio.Recusado.class);
            assertThat(local.verificarAutenticacao().autenticada()).isFalse();
        }
        semRede.verify();
    }

    @Test
    void credencialHistoricaDivergenteECitacaoAindaNaoCorrelacionadaNaoFazemHttp() {
        assertThat(adapter.enviar(envio(UUID.randomUUID(), null))).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(adapter.enviar(envio(null, null))).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        assertThat(adapter.enviar(envio(CREDENCIAL, "wamid-antigo"))).isInstanceOf(ResultadoDeEnvio.Recusado.class);
        servidor.verify();
    }

    @Test
    void templateNaoSuportadoNaoChamaOutroEndpoint() {
        assertThat(adapter.enviar(new CanalGateway.Envio(UUID.randomUUID(), "5561999999999",
                ConteudoDeEnvio.MensagemTemplate.de("nome", "pt_BR"), CREDENCIAL)))
                .isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_CONTEUDO_NAO_SUPORTADO"));
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123@lid", "abc", "https://destino", "", "+55 61 99999-9999"})
    void destinoNaoCanonicoNaoFazHttp(String telefone) {
        assertThat(adapter.enviar(new CanalGateway.Envio(UUID.randomUUID(), telefone,
                new ConteudoDeEnvio.MensagemLivre("teste"), CREDENCIAL)))
                .isEqualTo(ResultadoDeEnvio.Recusado.permanente("Z_API_DESTINO_INVALIDO"));
        servidor.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"connected\":false,\"smartphoneConnected\":true}", "{\"connected\":\"true\",\"error\":\"segredo\"}"})
    void saudeHttp200NaoProvaConexao(String corpo) {
        servidor.expect(requestTo(BASE + "/status")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Client-Token", "segredo-cliente"))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        assertThat(adapter.verificarAutenticacao().autenticada()).isFalse();
        servidor.verify();
    }

    @Test
    void saudeConectadaSoEmConsultaExplicitaESeparada() {
        servidor.expect(requestTo(BASE + "/status"))
                .andRespond(withSuccess("{\"connected\":true,\"smartphoneConnected\":false,\"error\":\"You are already connected\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.verificarAutenticacao().autenticada()).isTrue();
        assertThat(adapter.aceitaTextoLivre(Optional.empty(), Instant.now())).isTrue();
        assertThat(adapter.exigeTemplateForaDaJanela()).isFalse();
        assertThat(adapter.gerenciaTemplates()).isFalse();
        servidor.verify();
    }

    @Test
    void configuracaoNaoExibeSegredosEmToString() {
        assertThat(propriedades(true, "segredo-instancia", CREDENCIAL).toString())
                .doesNotContain("segredo-instancia", "segredo-cliente");
    }

    @Test
    void concorrenciaEsgotadaNaoEsperaNemChamaHttp() throws Exception {
        CountDownLatch entrou = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        servidor.expect(requestTo(BASE + "/send-text")).andRespond(pedido -> {
            entrou.countDown();
            try {
                if (!liberar.await(5, TimeUnit.SECONDS)) { throw new AssertionError("teste nao liberou HTTP"); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            return withSuccess("{\"zaapId\":\"z\",\"messageId\":\"w\"}", MediaType.APPLICATION_JSON).createResponse(pedido);
        });
        CompletableFuture<ResultadoDeEnvio> primeiro = CompletableFuture.supplyAsync(() -> adapter.enviar(envio(CREDENCIAL, null)));
        try {
            assertThat(entrou.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(adapter.enviar(envio(CREDENCIAL, null)))
                    .isEqualTo(ResultadoDeEnvio.Recusado.temporario("Z_API_LIMITE_DE_CONCORRENCIA"));
        } finally { liberar.countDown(); }
        assertThat(primeiro.get(5, TimeUnit.SECONDS)).isInstanceOf(ResultadoDeEnvio.Enfileirado.class);
        servidor.verify();
    }

    @Test
    void logsEEventosDoDisjuntorNaoRecebemSegredosNemCorpoMesmoEmDebug() {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level anterior = raiz.getLevel();
        ListAppender<ILoggingEvent> capturado = new ListAppender<>();
        capturado.start();
        raiz.addAppender(capturado);
        raiz.setLevel(Level.DEBUG);
        java.util.List<Throwable> erros = new java.util.ArrayList<>();
        breakers.circuitBreaker("canal-z-api").getEventPublisher().onError(evento -> erros.add(evento.getThrowable()));
        try {
            servidor.expect(requestTo(BASE + "/send-text"))
                    .andRespond(withException(new IOException(BASE + " segredo-cliente")));
            assertThat(adapter.enviar(envio(CREDENCIAL, null))).isInstanceOf(ResultadoDeEnvio.Ambiguo.class);
            assertThat(erros).singleElement().satisfies(erro -> {
                assertThat(erro.getCause()).isNull();
                assertThat(erro.getMessage()).isEqualTo("Z_API_RESPOSTA_NAO_CONFIRMADA");
            });
            assertThat(capturado.list.stream().map(ILoggingEvent::getFormattedMessage).toList().toString())
                    .doesNotContain("segredo-instancia", "segredo-cliente", "5561999999999", "Olá", "api.z-api.io");
            servidor.verify();
        } finally {
            raiz.setLevel(anterior);
            raiz.detachAppender(capturado);
            capturado.stop();
        }
    }

    private ZApiAdapter criar(RestClient.Builder cliente, ZApiProperties props) {
        return new ZApiAdapter(cliente, props, new ObjectMapper(), breakers);
    }

    private static ZApiProperties propriedades(boolean habilitado, String token, UUID credencial) {
        return new ZApiProperties(habilitado, "instancia-teste", token, "segredo-cliente", credencial,
                Duration.ofSeconds(10), 4096, 1);
    }

    private static CanalGateway.Envio envio(UUID credencial, String contexto) {
        return new CanalGateway.Envio(UUID.randomUUID(), "5561999999999",
                new ConteudoDeEnvio.MensagemLivre("Olá\nMundo"), credencial, contexto);
    }
}
