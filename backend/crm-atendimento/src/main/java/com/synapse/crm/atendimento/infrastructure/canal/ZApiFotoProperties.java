package com.synapse.crm.atendimento.infrastructure.canal;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Opt-in independente de mensagens e do coletor legado. */
@ConfigurationProperties("synapse.canal.z-api.foto-perfil")
public record ZApiFotoProperties(boolean habilitado, Duration cacheTtl, Duration cacheNegativo,
        Duration intervaloErro, Duration reserva, Duration timeout, int concorrencia, int limiteBytes,
        long pixelsMaximos, List<String> hostsPermitidos) {
    public ZApiFotoProperties {
        for (Duration valor : List.of(cacheTtl, cacheNegativo, intervaloErro, reserva, timeout)) {
            if (!valor.isPositive()) throw new IllegalArgumentException("Z_API_FOTO_DURACAO_INVALIDA");
        }
        if (concorrencia < 1 || limiteBytes < 1 || limiteBytes == Integer.MAX_VALUE || pixelsMaximos < 1
                || reserva.compareTo(timeout.multipliedBy(2)) <= 0)
            throw new IllegalArgumentException("Z_API_FOTO_LIMITE_INVALIDO");
        hostsPermitidos = List.copyOf(hostsPermitidos);
        if (hostsPermitidos.stream().anyMatch(h -> !h.matches("[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?")
                || !h.contains(".") || h.matches("[0-9.]+") || h.contains("..")))
            throw new IllegalArgumentException("Z_API_FOTO_HOST_INVALIDO");
    }
}
