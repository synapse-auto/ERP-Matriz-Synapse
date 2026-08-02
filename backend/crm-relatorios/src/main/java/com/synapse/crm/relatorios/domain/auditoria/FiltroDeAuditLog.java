package com.synapse.crm.relatorios.domain.auditoria;

import java.time.Instant;
import java.util.UUID;

/** Todos os campos sao opcionais — {@code null} significa "sem filtro nesse campo". */
public record FiltroDeAuditLog(
        UUID atorId, String acao, String entidadeTipo, UUID entidadeId, UUID leadId, Instant de, Instant ate) {}
