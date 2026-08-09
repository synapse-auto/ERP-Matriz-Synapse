package com.synapse.crm.core.application.mensagemprogramada;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.core.domain.mensagemprogramada.StatusMensagemProgramada;

/** {@code leadId} (E17 §Bloco 2): filtro opcional para a seção "Mensagens programadas" do painel. */
public record FiltroMensagensProgramadas(Instant inicio, Instant fim, StatusMensagemProgramada status,
        UUID leadId, int pagina, int tamanho) {
    public FiltroMensagensProgramadas {
        if (pagina < 0 || tamanho < 1) throw new IllegalArgumentException("paginacao invalida");
        if (inicio != null && fim != null && fim.isBefore(inicio)) throw new IllegalArgumentException("periodo invalido");
    }
}
