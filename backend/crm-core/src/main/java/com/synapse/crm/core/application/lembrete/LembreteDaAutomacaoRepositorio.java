package com.synapse.crm.core.application.lembrete;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.core.domain.lembrete.Lembrete;

/** Escrita automatica de lembrete no mesmo pool transacional dos comandos internos. */
public interface LembreteDaAutomacaoRepositorio {

    Lembrete criar(UUID leadId, UUID atendenteId, String texto, Instant dataHora);
}
