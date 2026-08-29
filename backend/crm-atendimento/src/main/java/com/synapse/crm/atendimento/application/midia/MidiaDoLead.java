package com.synapse.crm.atendimento.application.midia;

import java.time.Instant;
import java.util.UUID;

/** Leitura enxuta de anexos já persistidos em mensagens de atendimentos visíveis. */
public record MidiaDoLead(
        UUID mensagemId,
        UUID atendimentoId,
        String tipo,
        String nome,
        String mimetype,
        long tamanho,
        String legenda,
        String referenciaStorage,
        Instant enviadoEm,
        String origem) {}
