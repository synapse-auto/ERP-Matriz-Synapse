package com.synapse.crm.app.saude.infrastructure;

import java.util.List;
import java.util.Set;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.synapse.crm.app.config.SynapseProperties;
import com.synapse.crm.app.saude.application.AlertaDeSaude;
import com.synapse.crm.app.saude.application.DestinoDoAlerta;
import com.synapse.crm.app.saude.application.PublicadorDeAlertaDeSaude;
import com.synapse.crm.app.saude.application.SeveridadeSaude;

/** Única saída de alerta da aplicação, configurada pela variável já existente ALERTA_WEBHOOK. */
@Component
class WebhookDeAlertaDeSaude implements PublicadorDeAlertaDeSaude {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeAlertaDeSaude.class);
    private static final String NOME_BREAKER = "alerta-webhook";

    private final RestClient http;
    private final String url;
    private final String tenant;
    private final CircuitBreaker breaker;

    WebhookDeAlertaDeSaude(
            RestClient.Builder builder,
            SynapseProperties propriedades,
            CircuitBreakerRegistry breakers) {
        this.http = builder.build();
        this.url = propriedades.alertas().webhookGrupo();
        this.tenant = propriedades.tenant().codigo();
        this.breaker = breakers.circuitBreaker(NOME_BREAKER);
    }

    @Override
    public void publicar(AlertaDeSaude alerta) {
        if (url == null || url.isBlank()) {
            log.warn("ALERTA_WEBHOOK vazio; alerta de saude nao foi entregue.");
            return;
        }
        try {
            breaker.executeRunnable(() -> http.post()
                    .uri(url)
                    .body(CorpoDoAlerta.de(tenant, alerta))
                    .retrieve()
                    .toBodilessEntity());
        } catch (CallNotPermittedException e) {
            log.error("Circuit breaker do ALERTA_WEBHOOK aberto; alerta nao entregue.");
        } catch (RuntimeException e) {
            log.error("Falha ao entregar alerta pelo ALERTA_WEBHOOK: {}", e.getClass().getSimpleName());
        }
    }

    private record CorpoDoAlerta(
            String tenant,
            SeveridadeSaude severidade,
            Set<DestinoDoAlerta> destinos,
            List<String> componentes,
            String ocorridoEm) {

        static CorpoDoAlerta de(String tenant, AlertaDeSaude alerta) {
            return new CorpoDoAlerta(
                    tenant,
                    alerta.severidade(),
                    alerta.destinos(),
                    alerta.componentes(),
                    alerta.ocorridoEm().toString());
        }
    }
}
