package com.synapse.crm.atendimento.infrastructure.automacao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracao por instancia do destino que recebe o webhook cru da Meta. */
@ConfigurationProperties("synapse.automacao.repasse-webhook")
public record RepasseWebhookAutomacaoProperties(String url, Duration timeout, String token) {

    public RepasseWebhookAutomacaoProperties(String url, Duration timeout) { this(url, timeout, ""); }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public RepasseWebhookAutomacaoProperties {
        url = url == null ? "" : url.trim();
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        token = token == null ? "" : token;
    }

    public boolean configurado() {
        return !url.isBlank();
    }

    public boolean temToken() { return !token.isBlank() && token.chars().noneMatch(c -> c <= 32 || c >= 127); }
    @Override public String toString() { return "RepasseWebhookAutomacaoProperties[segredos=REDACTED]"; }
}
