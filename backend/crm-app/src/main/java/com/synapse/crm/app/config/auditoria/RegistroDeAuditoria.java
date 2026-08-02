package com.synapse.crm.app.config.auditoria;

import java.time.Instant;
import java.util.UUID;

/** Uma linha pronta para {@code audit_log} — espelha as colunas 1:1 (ver V9__infra_transversal.sql). */
record RegistroDeAuditoria(
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
