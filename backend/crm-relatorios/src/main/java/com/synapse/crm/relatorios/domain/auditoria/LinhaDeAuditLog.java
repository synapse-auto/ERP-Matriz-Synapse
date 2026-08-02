package com.synapse.crm.relatorios.domain.auditoria;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha de {@code audit_log} como leitura. {@code dadosAntes}/{@code dadosDepois} chegam como
 * texto JSON cru (a allowlist de campos ja foi aplicada na escrita, por {@code SerializadorAuditavel}
 * — a leitura nao reprocessa nem filtra de novo).
 */
public record LinhaDeAuditLog(
        long id,
        UUID atorId,
        String atorTipo,
        String acao,
        String entidadeTipo,
        UUID entidadeId,
        UUID leadId,
        String dadosAntes,
        String dadosDepois,
        String ip,
        Instant criadoEm) {}
