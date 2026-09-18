package com.synapse.crm.atendimento.infrastructure.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ResumoIaAutomacaoHttpAdapterTest {
    private static final UUID SOLICITACAO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LEAD = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ATENDIMENTO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant SOLICITADO_EM = Instant.parse("2026-09-17T18:00:00Z");

    @Test
    void enviaSomenteIdsEMarcoComChaveIdempotente() throws Exception {
        AtomicReference<HttpExchange> requisicao = new AtomicReference<>();
        AtomicReference<String> corpo = new AtomicReference<>();
        try (ServidorFake servidor = servidor(202, requisicao, corpo)) {
            var adapter = adapter(servidor.url(), "token-de-teste");

            assertThat(adapter.enviar(SOLICITACAO, LEAD, ATENDIMENTO, SOLICITADO_EM))
                    .isEqualTo(com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado.ACEITO);
            assertThat(requisicao.get().getRequestMethod()).isEqualTo("POST");
            assertThat(requisicao.get().getRequestURI().getPath()).isEqualTo("/resumo");
            assertThat(requisicao.get().getRequestHeaders().getFirst("X-Synapse-Token")).isEqualTo("token-de-teste");
            assertThat(requisicao.get().getRequestHeaders().getFirst("Idempotency-Key"))
                    .isEqualTo(SOLICITACAO.toString());
            assertThat(corpo.get()).contains(
                    "\"evento\":\"RESUMO_IA_SOLICITADO\"",
                    "\"solicitacaoId\":\"" + SOLICITACAO + "\"",
                    "\"leadId\":\"" + LEAD + "\"",
                    "\"atendimentoId\":\"" + ATENDIMENTO + "\"",
                    "\"solicitadoEm\":\"" + SOLICITADO_EM + "\"");
        }
    }

    @Test
    void quatrocentosERecusaSemRetry() throws Exception {
        try (ServidorFake servidor = servidor(400, new AtomicReference<>(), new AtomicReference<>())) {
            assertThat(adapter(servidor.url(), "token-de-teste")
                            .enviar(SOLICITACAO, LEAD, ATENDIMENTO, SOLICITADO_EM))
                    .isEqualTo(com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado.RECUSADO);
        }
    }

    @Test
    void cincoCentosSaoTentaveisNovamente() throws Exception {
        try (ServidorFake servidor = servidor(500, new AtomicReference<>(), new AtomicReference<>())) {
            assertThat(adapter(servidor.url(), "token-de-teste")
                            .enviar(SOLICITACAO, LEAD, ATENDIMENTO, SOLICITADO_EM))
                    .isEqualTo(com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado.TENTAR_NOVAMENTE);
        }
    }

    private static ResumoIaAutomacaoHttpAdapter adapter(String url, String token) {
        return new ResumoIaAutomacaoHttpAdapter(
                RestClient.builder(),
                new ResumoIaAutomacaoProperties(
                        url,
                        token,
                        Duration.ofSeconds(1),
                        1,
                        3,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5),
                        1000),
                CircuitBreakerRegistry.ofDefaults());
    }

    private static ServidorFake servidor(
            int status, AtomicReference<HttpExchange> requisicao, AtomicReference<String> corpo) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/resumo", exchange -> {
            requisicao.set(exchange);
            corpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return new ServidorFake(server);
    }

    private record ServidorFake(HttpServer server) implements AutoCloseable {
        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/resumo";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
