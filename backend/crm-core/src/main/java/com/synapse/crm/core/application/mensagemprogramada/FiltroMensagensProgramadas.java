package com.synapse.crm.core.application.mensagemprogramada;

import java.time.Instant;

import com.synapse.crm.core.domain.mensagemprogramada.StatusMensagemProgramada;

public record FiltroMensagensProgramadas(Instant inicio, Instant fim, StatusMensagemProgramada status,
        int pagina, int tamanho) {
    public FiltroMensagensProgramadas {
        if (pagina < 0 || tamanho < 1) throw new IllegalArgumentException("paginacao invalida");
        if (inicio != null && fim != null && fim.isBefore(inicio)) throw new IllegalArgumentException("periodo invalido");
    }
}
