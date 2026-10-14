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
import org.springframework.web.client.ResourceAccessException;
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
            var resposta = breaker.executeSupplier(() -> http.post()
                    .uri(propriedades.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(propriedades.authHeader(), propriedades.token())
                    .header("Idempotency-Key", solicitacaoId.toString())
                    .body(new Pedido(atendimentoId, leadId))
                    .retrieve()
                    .toBodilessEntity());
            int status = resposta.getStatusCode().value();
            log.info("[RESUMO_IA_HTTP] solicitacaoId={} atendimentoId={} leadId={} etapa=webhook http={}",
                    solicitacaoId, atendimentoId, leadId, status);
            // O contrato é 202 (nova) ou 200 (replay); redirect/204 não comprovam aceite.
            return status == 200 || status == 202 ? Resultado.ACEITO : Resultado.RECUSADO;
        } catch (CallNotPermittedException erro) {
            log.warn("Circuit breaker do resumo por IA está aberto.");
            return Resultado.TENTAR_NOVAMENTE;
        } catch (HttpClientErrorException erro) {
            int status = erro.getStatusCode().value();
            log.warn("[RESUMO_IA_HTTP] solicitacaoId={} atendimentoId={} leadId={} etapa=webhook http={}",
                    solicitacaoId, atendimentoId, leadId, status);
            return status == 401 || status == 403 ? Resultado.AUTENTICACAO_RECUSADA : Resultado.RECUSADO;
        } catch (HttpServerErrorException erro) {
            log.warn("[RESUMO_IA_HTTP] solicitacaoId={} atendimentoId={} leadId={} etapa=webhook http={}",
                    solicitacaoId, atendimentoId, leadId, erro.getStatusCode().value());
            return Resultado.TENTAR_NOVAMENTE;
        } catch (ResourceAccessException erro) {
            log.warn("[RESUMO_IA_HTTP] solicitacaoId={} atendimentoId={} leadId={} etapa=webhook resultado=INCERTO falha={}",
                    solicitacaoId, atendimentoId, leadId, erro.getClass().getSimpleName());
            // Timeout/desconexão não prova que o n8n deixou de aceitar o pedido.
            return Resultado.RESULTADO_INCERTO;
        } catch (RuntimeException erro) {
            // Mensagens de exceção podem carregar URL, query string e resposta do provedor.
            log.warn("[RESUMO_IA_HTTP] solicitacaoId={} atendimentoId={} leadId={} etapa=webhook falha={}",
                    solicitacaoId, atendimentoId, leadId, erro.getClass().getSimpleName());
            return Resultado.TENTAR_NOVAMENTE;
        }
    }

    private record Pedido(UUID atendimentoId, UUID leadId) {}
}
