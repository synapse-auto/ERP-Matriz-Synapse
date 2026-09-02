package com.synapse.crm.atendimento.infrastructure.avaliacao;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** ACL de avaliacao; nem o corpo da resposta nem excecoes do cliente HTTP saem deste adaptador. */
@Component
class AvaliacaoWebhookHttp {
    private final AvaliacaoWebhookProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final CircuitBreaker circuito;

    AvaliacaoWebhookHttp(AvaliacaoWebhookProperties config, ObjectMapper json, CircuitBreakerRegistry registro) {
        this.config = config;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.circuito = registro.circuitBreaker("automacao-avaliacao",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(config.minimoChamadasCircuito())
                        .minimumNumberOfCalls(config.minimoChamadasCircuito())
                        .waitDurationInOpenState(config.esperaCircuito())
                        .recordResult(resultado -> resultado instanceof Resultado r && !r.sucesso() && !r.permanente())
                        .build());
    }

    record Resultado(boolean sucesso, boolean permanente, String classe, int status) {
        static Resultado falha(String classe, boolean permanente, int status) {
            return new Resultado(false, permanente, classe, status);
        }
    }

    Resultado enviar(String payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("HTTP de avaliacao nao pode reter transacao");
        }
        if (!config.configurada()) {
            return Resultado.falha("CONFIGURACAO_INVALIDA", true, 0);
        }
        if (!payloadValido(payload)) {
            return Resultado.falha("PAYLOAD_INVALIDO", true, 0);
        }
        try {
            return circuito.executeSupplier(() -> chamar(payload));
        } catch (CallNotPermittedException e) {
            return Resultado.falha("CIRCUITO_ABERTO", false, 0);
        }
    }

    private Resultado chamar(String payload) {
        var request = HttpRequest.newBuilder(URI.create(config.url()))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .header(config.authHeader(), config.token())
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
        var resposta = http.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        try {
            // Limite total inclui o corpo, nao apenas a espera pelos headers.
            int status = resposta.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS).statusCode();
            if (status >= 200 && status < 300) {
                return new Resultado(true, false, "HTTP_2XX", status);
            }
            boolean recuperavel = status == 408 || status == 429 || status >= 500;
            return Resultado.falha("HTTP_" + status, !recuperavel, status);
        } catch (TimeoutException e) {
            resposta.cancel(true);
            return Resultado.falha("TIMEOUT", false, 0);
        } catch (InterruptedException e) {
            resposta.cancel(true);
            Thread.currentThread().interrupt();
            return Resultado.falha("INTERROMPIDO", false, 0);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof java.net.http.HttpTimeoutException) {
                return Resultado.falha("TIMEOUT", false, 0);
            }
            return Resultado.falha("FALHA_TRANSPORTE", false, 0);
        }
    }

    /**
     * Guarda de forma do contrato EV-08 secao 3, aplicada no momento da publicacao e nao no da gravacao.
     *
     * <p>Existe para a linha antiga: uma pendencia enfileirada antes deste deploy carrega o formato
     * de 6 campos com {@code modo}, e nao pode sair pela rede como se fosse o contrato novo. Ela
     * reprova aqui e vira falha <b>permanente</b> ({@code PAYLOAD_INVALIDO}), nunca retentativa
     * eterna: o publicador esgota a linha, que fica inspecionavel no outbox.
     */
    private boolean payloadValido(String payload) {
        try {
            var no = json.readTree(payload);
            if (!no.isObject() || no.size() != 8
                    || !"FINALIZADO".equals(no.path("status_finalizacao").asText())
                    || !"FINALIZAR_INDIVIDUAL".equals(no.path("operacao").asText())
                    || !no.path("finalizacao_em_massa").isBoolean()
                    || no.path("finalizacao_em_massa").asBoolean()
                    || !no.path("wa_id").asText().matches("[1-9][0-9]{9,14}")) {
                return false;
            }
            for (String campo : Set.of("evento_id", "atendimento_id", "lead_id", "atendente_id")) {
                UUID.fromString(no.path(campo).asText());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @PreDestroy
    void fechar() {
        http.shutdownNow();
    }
}
