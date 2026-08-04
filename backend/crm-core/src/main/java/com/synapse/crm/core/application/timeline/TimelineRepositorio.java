package com.synapse.crm.core.application.timeline;

import java.util.UUID;

/** Porta de leitura da historia append-only de um lead. */
public interface TimelineRepositorio {

    PaginaTimeline listar(UUID leadId, int pagina, int tamanho);
}
