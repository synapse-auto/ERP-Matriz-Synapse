package com.synapse.crm.core.domain.evento;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.synapse.crm.core.domain.etapa.EtapaAtendimento;

/** Fato imutavel de que um lead mudou de etapa do funil. */
public record EtapaDoLeadAlterada(
        UUID leadId,
        EtapaAtendimento etapaAnterior,
        EtapaAtendimento etapaNova,
        UUID responsavelId,
        UUID atorId,
        Instant ocorridoEm) {

    public EtapaDoLeadAlterada {
        Objects.requireNonNull(leadId, "lead da mudanca de etapa e obrigatorio");
        Objects.requireNonNull(etapaNova, "etapa nova e obrigatoria");
        Objects.requireNonNull(atorId, "ator da mudanca de etapa e obrigatorio");
        Objects.requireNonNull(ocorridoEm, "instante da mudanca de etapa e obrigatorio");
    }
}
