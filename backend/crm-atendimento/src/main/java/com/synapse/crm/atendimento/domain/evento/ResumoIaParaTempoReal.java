package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.UUID;

/** Estado público mínimo do ciclo de resumo; não transporta histórico nem o texto gerado. */
public record ResumoIaParaTempoReal(
        UUID atendimentoId,
        UUID leadId,
        UUID solicitacaoId,
        String status,
        String erroCodigo,
        Instant ocorridoEm) {}
