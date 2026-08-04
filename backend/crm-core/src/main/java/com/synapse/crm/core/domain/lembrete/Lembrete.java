package com.synapse.crm.core.domain.lembrete;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lembrete pessoal do atendente (RN-CRM-04). */
public record Lembrete(
        UUID id,
        UUID leadId,
        String leadNome,
        UUID atendenteId,
        String atendenteNome,
        String texto,
        Instant dataHora,
        boolean origemAutomatica,
        StatusLembrete status) {

    public Lembrete {
        Objects.requireNonNull(id, "id e obrigatorio");
        Objects.requireNonNull(atendenteId, "atendenteId e obrigatorio");
        Objects.requireNonNull(texto, "texto e obrigatorio");
        Objects.requireNonNull(dataHora, "dataHora e obrigatoria");
        Objects.requireNonNull(status, "status e obrigatorio");
    }
}
