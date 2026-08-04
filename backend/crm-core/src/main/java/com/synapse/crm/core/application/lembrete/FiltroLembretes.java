package com.synapse.crm.core.application.lembrete;

import java.time.Instant;

import com.synapse.crm.core.domain.lembrete.StatusLembrete;

public record FiltroLembretes(Instant inicio, Instant fim, StatusLembrete status, int pagina, int tamanho) {
    public FiltroLembretes {
        if (pagina < 0 || tamanho < 1) {
            throw new IllegalArgumentException("pagina e tamanho devem ser positivos");
        }
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new IllegalArgumentException("fim deve ser posterior ao inicio");
        }
    }
}
