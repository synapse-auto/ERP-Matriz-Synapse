package com.synapse.crm.app.mensagemprogramada;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração operacional limitada do scheduler; não é configuração de negócio. */
@ConfigurationProperties(prefix = "synapse.suporte.mensagens-programadas")
public record MensagensProgramadasProperties(int lote) {
    public MensagensProgramadasProperties {
        if (lote < 1 || lote > 500) {
            throw new IllegalArgumentException("lote de mensagens programadas deve estar entre 1 e 500");
        }
    }
}
