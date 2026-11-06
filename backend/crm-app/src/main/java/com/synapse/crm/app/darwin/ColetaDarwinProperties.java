package com.synapse.crm.app.darwin;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("synapse.integracoes.darwin.coleta")
public record ColetaDarwinProperties(@DefaultValue("true") boolean habilitada, @DefaultValue("24h") Duration cache,
                                    @DefaultValue("5m") Duration intervaloErro) {
    public ColetaDarwinProperties {
        if (cache==null || !cache.isPositive() || intervaloErro==null || !intervaloErro.isPositive())
            throw new IllegalArgumentException("Intervalos da coleta Darwin devem ser positivos");
    }
}
