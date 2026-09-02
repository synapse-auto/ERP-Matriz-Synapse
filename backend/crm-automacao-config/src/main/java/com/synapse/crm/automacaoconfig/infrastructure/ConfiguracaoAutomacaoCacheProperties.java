package com.synapse.crm.automacaoconfig.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TTL do cache Redis de {@code configuracao_automacao} (E104).
 *
 * <p>A invalidacao por evento continua sendo o caminho normal. O TTL e rede de seguranca: se a
 * invalidacao falhar ou um valor residual ficar no Redis, a entrada nao fica eterna.
 */
@ConfigurationProperties("synapse.automacao.configuracao")
public record ConfiguracaoAutomacaoCacheProperties(@DefaultValue("5m") Duration cacheTtl) {

    public ConfiguracaoAutomacaoCacheProperties {
        if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()) {
            throw new IllegalArgumentException("synapse.automacao.configuracao.cache-ttl deve ser positivo");
        }
    }
}
