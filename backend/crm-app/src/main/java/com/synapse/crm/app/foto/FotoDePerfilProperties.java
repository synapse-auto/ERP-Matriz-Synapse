package com.synapse.crm.app.foto;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração operacional da captura assíncrona de foto pelo canal ativo. */
@ConfigurationProperties(prefix = "synapse.canal.foto-perfil")
public record FotoDePerfilProperties(
        boolean habilitado, Duration cacheTtl, int concorrencia, int fila, int limiteBytes) {

    public FotoDePerfilProperties {
        cacheTtl = cacheTtl == null ? Duration.ofHours(6) : cacheTtl;
        if (cacheTtl.isNegative() || cacheTtl.isZero()) {
            throw new IllegalArgumentException("cacheTtl precisa ser positivo");
        }
        if (concorrencia < 1 || fila < 1) {
            throw new IllegalArgumentException("concorrencia e fila precisam ser positivas");
        }
        if (limiteBytes < 1) {
            throw new IllegalArgumentException("limiteBytes precisa ser positivo");
        }
    }

}
