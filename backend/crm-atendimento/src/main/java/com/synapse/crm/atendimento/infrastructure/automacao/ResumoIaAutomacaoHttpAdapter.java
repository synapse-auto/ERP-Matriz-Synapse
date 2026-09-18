package com.synapse.crm.atendimento.infrastructure.automacao;

import java.time.Instant;
import java.util.UUID;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway;

/** Adaptador HTTP assíncrono do pedido de resumo; nunca é chamado pelo request do navegador. */
@Component
class ResumoIaAutomacaoHttpAdapter implements ResumoIaAutomacaoGateway {

    private static final Logger log = LoggerFactory.getLogger(ResumoIaAutomacaoHttpAdapter.class);
    private static final String BREAKER = "automacao-resumo-ia";

    private final RestClient http;
    private final ResumoIaAutomacaoProperties propriedades;
    private final CircuitBreaker breaker;

    ResumoIaAutomacaoHttpAdapter(
            RestClient.Builder builder,
            ResumoIaAutomacaoProperties propriedades,
            CircuitBreakerRegistry breakers) {
        SimpleClientHttpRequestFactory requisicoes = new SimpleClientHttpRequestFactory();
        requisicoes.setConnectTimeout(propriedades.timeout());
        requisicoes.setReadTimeout(propriedades.timeout());
        this.http = builder.requestFactory(requisicoes).build();
        this.propriedades = propriedades;
        this.breaker = breakers.circuitBreaker(BREAKER);
    }

    @Override
    public boolean configurado() {
        return propriedades.configurado();
    }

    @Override
    public Resultado enviar(UUID solicitacaoId, UUID leadId, UUID atendimentoId, Instant solicitadoEm) {
        if (!configurado()) return Resultado.RECUSADO;
        try {
            breaker.executeRunnable(() -> http.post()
                    .uri(propriedades.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(propriedades.authHeader(), propriedades.token())
                    .header("Idempotency-Key", solicitacaoId.toString())
                    .body(new Pedido(atendimentoId, leadId))
                    .retrieve()
                    .toBodilessEntity());
            return Resultado.ACEITO;
        } catch (CallNotPermittedException erro) {
            log.warn("Circuit breaker do resumo por IA está aberto.");
            return Resultado.TENTAR_NOVAMENTE;
        } catch (HttpClientErrorException erro) {
            log.warn("Webhook de resumo por IA recusou a solicitação com HTTP {}.", erro.getStatusCode().value());
            return Resultado.RECUSADO;
        } catch (HttpServerErrorException erro) {
            log.warn("Webhook de resumo por IA indisponível com HTTP {}; a outbox tentará novamente.",
                    erro.getStatusCode().value());
            return Resultado.TENTAR_NOVAMENTE;
        } catch (RuntimeException erro) {
            log.warn("Webhook de resumo por IA indisponível; a outbox tentará novamente: {}", erro.toString());
            return Resultado.TENTAR_NOVAMENTE;
        }
    }

    private record Pedido(UUID atendimentoId, UUID leadId) {}
}
