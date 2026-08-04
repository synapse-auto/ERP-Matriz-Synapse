package com.synapse.crm.core.application.timeline;

import java.util.List;

import com.synapse.crm.core.domain.timeline.EventoTimeline;

/** Pagina sem contagem global: uma linha adicional basta para dizer se ha proxima pagina. */
public record PaginaTimeline(List<EventoTimeline> eventos, int pagina, boolean temMais) {

    public PaginaTimeline {
        eventos = List.copyOf(eventos);
    }
}
