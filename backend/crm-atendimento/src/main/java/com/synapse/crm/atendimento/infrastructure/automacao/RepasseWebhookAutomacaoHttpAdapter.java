package com.synapse.crm.atendimento.infrastructure.automacao;

import java.net.SocketTimeoutException;
import java.util.Locale;

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
    /** Chave estavel do evento para o consumidor deduplicar (docs/50). */
    static final String CABECALHO_EVENTO_ID = "X-Synapse-Evento-Id";
    static final String CABECALHO_TENTATIVA = "X-Synapse-Tentativa";
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
    public ResultadoRepasse repassar(Repasse repasse) {
        if (!configurado()) {
            return ResultadoRepasse.ACEITO;
        }
        try {
            // Corpo e assinatura seguem byte a byte; os cabecalhos X-Synapse-* sao aditivos.
            breaker.executeRunnable(() -> http.post()
                    .uri(propriedades.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Hub-Signature-256", repasse.assinatura())
                    .header(CABECALHO_EVENTO_ID, repasse.eventoId().toString())
                    .header(CABECALHO_TENTATIVA, Integer.toString(repasse.tentativa()))
                    .body(repasse.payloadCru())
                    .retrieve()
                    .toBodilessEntity());
            return ResultadoRepasse.ACEITO;
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker do repasse para a Automacao esta aberto.");
            return ResultadoRepasse.TENTAR_NOVAMENTE;
        } catch (RuntimeException e) {
            if (timeoutDeLeitura(e)) {
                log.warn(
                        "Repasse {} (tentativa {}) com entrega incerta: a Automacao recebeu e nao respondeu"
                                + " a tempo; a reentrega leva o mesmo {}.",
                        repasse.eventoId(),
                        repasse.tentativa(),
                        CABECALHO_EVENTO_ID);
                return ResultadoRepasse.INCERTO;
            }
            log.warn(
                    "Repasse do webhook para a Automacao falhou; a outbox tentara novamente: {}",
                    e.toString());
            return ResultadoRepasse.TENTAR_NOVAMENTE;
        }
    }

    /**
     * Timeout de LEITURA acontece depois de o corpo ter sido escrito: o destino pode ter processado.
     * Timeout de conexao ("Connect timed out") nao entra aqui: nada chegou ao destino.
     */
    private static boolean timeoutDeLeitura(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException
                    && causa.getMessage() != null
                    && causa.getMessage().toLowerCase(Locale.ROOT).contains("read")) {
                return true;
            }
        }
        return false;
    }
}
