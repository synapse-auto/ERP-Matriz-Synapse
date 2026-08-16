package com.synapse.crm.atendimento.infrastructure.automacao;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.synapse.crm.atendimento.application.RepasseWebhookAutomacaoGateway;

/** Adaptador HTTP que preserva corpo e assinatura; nao interpreta o contrato da Meta. */
@Component
class RepasseWebhookAutomacaoHttpAdapter implements RepasseWebhookAutomacaoGateway {

    private static final Logger log =
            LoggerFactory.getLogger(RepasseWebhookAutomacaoHttpAdapter.class);
    private static final String NOME_DO_BREAKER = "automacao-webhook";

    private final RestClient http;
    private final RepasseWebhookAutomacaoProperties propriedades;
    private final CircuitBreaker breaker;

    RepasseWebhookAutomacaoHttpAdapter(
            RestClient.Builder builder,
            RepasseWebhookAutomacaoProperties propriedades,
            CircuitBreakerRegistry breakers) {
        SimpleClientHttpRequestFactory requisicoes = new SimpleClientHttpRequestFactory();
        requisicoes.setConnectTimeout(propriedades.timeout());
        requisicoes.setReadTimeout(propriedades.timeout());
        this.http = builder.requestFactory(requisicoes).build();
        this.propriedades = propriedades;
        this.breaker = breakers.circuitBreaker(NOME_DO_BREAKER);
    }

    @Override
    public boolean configurado() {
        return propriedades.configurado();
    }

    @Override
    public ResultadoRepasse repassar(String payloadCru, String assinatura) {
        if (!configurado()) {
            return ResultadoRepasse.ACEITO;
        }
        try {
            breaker.executeRunnable(() -> http.post()
                    .uri(propriedades.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hub-Signature-256", assinatura)
                    .body(payloadCru)
                    .retrieve()
                    .toBodilessEntity());
            return ResultadoRepasse.ACEITO;
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker do repasse para a Automacao esta aberto.");
            return ResultadoRepasse.TENTAR_NOVAMENTE;
        } catch (RuntimeException e) {
            log.warn(
                    "Repasse do webhook para a Automacao falhou; a outbox tentara novamente: {}",
                    e.toString());
            return ResultadoRepasse.TENTAR_NOVAMENTE;
        }
    }
}
