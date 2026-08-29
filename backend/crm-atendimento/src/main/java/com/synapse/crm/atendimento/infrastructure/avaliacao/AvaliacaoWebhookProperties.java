package com.synapse.crm.atendimento.infrastructure.avaliacao;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Segredo privado do backend. Ausencia/configuracao invalida desabilita apenas esta integracao. */
@ConfigurationProperties("synapse.automacao.avaliacao")
public record AvaliacaoWebhookProperties(
        @DefaultValue("") String url,
        @JsonIgnore @DefaultValue("") String token,
        @DefaultValue("") String authHeader,
        @DefaultValue("5s") Duration timeout,
        @DefaultValue("30s") Duration reservaExpiracao,
        @DefaultValue("10") int lote,
        @DefaultValue("2") int concorrencia,
        @DefaultValue("10") int fila,
        @DefaultValue("5") int maximoTentativas,
        @DefaultValue("10s") Duration backoffInicial,
        @DefaultValue("30m") Duration backoffMaximo,
        @DefaultValue("5") int minimoChamadasCircuito,
        @DefaultValue("30s") Duration esperaCircuito) {

    public AvaliacaoWebhookProperties {
        url = url == null ? "" : url.trim();
        token = token == null ? "" : token;
        authHeader = authHeader == null ? "" : authHeader.trim();
        if (timeout.isNegative() || timeout.isZero() || reservaExpiracao.compareTo(timeout) <= 0
                || lote < 1 || concorrencia < 1 || fila < 1 || maximoTentativas < 1
                || maximoTentativas > Short.MAX_VALUE || minimoChamadasCircuito < 1
                || backoffInicial.isNegative() || backoffInicial.isZero()
                || backoffMaximo.compareTo(backoffInicial) < 0
                || esperaCircuito.isNegative() || esperaCircuito.isZero()) {
            throw new IllegalArgumentException("limites operacionais invalidos para avaliacao");
        }
    }

    public boolean configurada() {
        if (url.isBlank() || token.isBlank() || !token.matches("[\\x20-\\x7E]+")
                || !authHeader.matches("[a-zA-Z0-9!#$%&'*+.^_`|~-]+")
                || java.util.Set.of("host", "content-length", "content-type", "connection", "expect", "upgrade")
                        .contains(authHeader.toLowerCase(Locale.ROOT))) {
            return false;
        }
        try {
            URI destino = URI.create(url);
            return ("https".equals(destino.getScheme()) || "http".equals(destino.getScheme()))
                    && destino.getHost() != null && destino.getUserInfo() == null
                    && destino.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Duration esperaApos(int tentativas) {
        Duration espera = backoffInicial.multipliedBy(1L << Math.min(Math.max(0, tentativas - 1), 20));
        return espera.compareTo(backoffMaximo) > 0 ? backoffMaximo : espera;
    }

    @Override
    public String toString() {
        return "AvaliacaoWebhookProperties[configurada=" + configurada() + "]";
    }
}
