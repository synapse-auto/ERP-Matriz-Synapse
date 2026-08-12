package com.synapse.crm.relatorios.domain;

import java.time.Instant;
import java.util.Objects;

/** Janela temporal de um read model, sempre com inicio inclusivo e fim exclusivo. */
public record IntervaloTemporal(Instant inicioInclusivo, Instant fimExclusivo) {

    public IntervaloTemporal {
        Objects.requireNonNull(inicioInclusivo, "inicio e obrigatorio");
        Objects.requireNonNull(fimExclusivo, "fim e obrigatorio");
        if (!inicioInclusivo.isBefore(fimExclusivo)) {
            throw new IllegalArgumentException("intervalo deve ter inicio anterior ao fim");
        }
    }
}
