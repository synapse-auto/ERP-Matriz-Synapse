package com.synapse.crm.atendimento.infrastructure.automacao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracao por instancia do destino que recebe o webhook cru da Meta. */
@ConfigurationProperties("synapse.automacao.repasse-webhook")
public record RepasseWebhookAutomacaoProperties(String url, Duration timeout) {

    public RepasseWebhookAutomacaoProperties {
        url = url == null ? "" : url.trim();
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
    }

    public boolean configurado() {
        return !url.isBlank();
    }
}
