package com.synapse.crm.atendimento.infrastructure.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ResumoIaAutomacaoHttpAdapterTest {
    private static final UUID SOLICITACAO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LEAD = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ATENDIMENTO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant SOLICITADO_EM = Instant.parse("2026-09-17T18:00:00Z");

    @Test
    void enviaSomenteIdsComHeaderConfiguradoEChaveIdempotente() throws Exception {
        AtomicReference<HttpExchange> requisicao = new AtomicReference<>();
        AtomicReference<String> corpo = new AtomicReference<>();
        try (ServidorFake servidor = servidor(202, requisicao, corpo)) {
            var adapter = adapter(servidor.url(), "token-de-teste");

            assertThat(adapter.enviar(SOLICITACAO, LEAD, ATENDIMENTO, SOLICITADO_EM))
                    .isEqualTo(com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado.ACEITO);
            assertThat(requisicao.get().getRequestMethod()).isEqualTo("POST");
            assertThat(requisicao.get().getRequestURI().getPath()).isEqualTo("/resumo");
            assertThat(requisicao.get().getRequestHeaders().getFirst("CRM-Synapse-RES")).isEqualTo("token-de-teste");
            assertThat(requisicao.get().getRequestHeaders().getFirst("Idempotency-Key"))
                    .isEqualTo(SOLICITACAO.toString());
            JsonNode pedido = new ObjectMapper().readTree(corpo.get());
            var campos = new HashSet<String>();
            pedido.fieldNames().forEachRemaining(campos::add);
            assertThat(campos).containsExactlyInAnyOrder("atendimentoId", "leadId");
            assertThat(pedido.get("atendimentoId").asText()).isEqualTo(ATENDIMENTO.toString());
            assertThat(pedido.get("leadId").asText()).isEqualTo(LEAD.toString());
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

    @Test
    void timeoutERepetivel() throws Exception {
        CountDownLatch recebeu = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        try (ServidorFake servidor = servidor(202, new AtomicReference<>(), new AtomicReference<>(), recebeu, liberar)) {
            var adapter = adapter(servidor.url(), "token-de-teste", Duration.ofMillis(25), CircuitBreakerRegistry.ofDefaults());

            assertThat(adapter.enviar(SOLICITACAO, LEAD, ATENDIMENTO, SOLICITADO_EM))
                    .isEqualTo(com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado.TENTAR_NOVAMENTE);
            assertThat(recebeu.await(1, TimeUnit.SECONDS)).isTrue();
            liberar.countDown();
        }
    }

    @Test
    void circuitoAbertoNaoFazNovaChamada() throws Exception {
        AtomicReference<HttpExchange> requisicao = new AtomicReference<>();
        try (ServidorFake servidor = servidor(500, requisicao, new AtomicReference<>())) {
            var registro = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                    .minimumNumberOfCalls(1)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(1))
                    .build());
            var adapter = adapter(servidor.url(), "token-de-teste", Duration.ofSeconds(1), registro);

            assertThat(adapter.enviar(SOLICITACAO, LEAD, ATENDIMENTO, SOLICITADO_EM))
                    .isEqualTo(com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado.TENTAR_NOVAMENTE);
            var primeira = requisicao.get();
            assertThat(adapter.enviar(UUID.randomUUID(), LEAD, ATENDIMENTO, SOLICITADO_EM))
                    .isEqualTo(com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado.TENTAR_NOVAMENTE);
            assertThat(requisicao.get()).isSameAs(primeira);
        }
    }

    private static ResumoIaAutomacaoHttpAdapter adapter(String url, String token) {
        return adapter(url, token, Duration.ofSeconds(1), CircuitBreakerRegistry.ofDefaults());
    }

    private static ResumoIaAutomacaoHttpAdapter adapter(
            String url, String token, Duration timeout, CircuitBreakerRegistry breakers) {
        return new ResumoIaAutomacaoHttpAdapter(
                RestClient.builder(),
                new ResumoIaAutomacaoProperties(
                        url,
                        token,
                        "CRM-Synapse-RES",
                        timeout,
                        1,
                        3,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5),
                        1000),
                breakers);
    }

    private static ServidorFake servidor(
            int status, AtomicReference<HttpExchange> requisicao, AtomicReference<String> corpo) throws IOException {
        return servidor(status, requisicao, corpo, null, null);
    }

    private static ServidorFake servidor(
            int status,
            AtomicReference<HttpExchange> requisicao,
            AtomicReference<String> corpo,
            CountDownLatch recebeu,
            CountDownLatch liberar) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/resumo", exchange -> {
            requisicao.set(exchange);
            corpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (recebeu != null) recebeu.countDown();
            if (liberar != null) {
                try {
                    liberar.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException erro) {
                    Thread.currentThread().interrupt();
                }
            }
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
