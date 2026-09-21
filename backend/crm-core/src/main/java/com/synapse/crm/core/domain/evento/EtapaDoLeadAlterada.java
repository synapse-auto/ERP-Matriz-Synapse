package com.synapse.crm.core.domain.evento;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.timeline.OrigemEvento;

/**
 * Fato imutavel de que um lead mudou de etapa do funil.
 *
 * @param atorId usuario humano que executou; nulo quando {@code atorTipo} nao e {@code USUARIO} —
 *     mesma convencao de {@code EventoDeAtendimento.AtendimentoTransferido}: a Automacao e o
 *     Sistema nao tem usuario para preencher aqui, e inventar um UUID fixo esconderia isso da
 *     timeline.
 */
public record EtapaDoLeadAlterada(
        UUID leadId,
        EtapaAtendimento etapaAnterior,
        EtapaAtendimento etapaNova,
        UUID responsavelId,
        UUID atorId,
        OrigemEvento atorTipo,
        Instant ocorridoEm) {

    public EtapaDoLeadAlterada {
        Objects.requireNonNull(leadId, "lead da mudanca de etapa e obrigatorio");
        Objects.requireNonNull(etapaNova, "etapa nova e obrigatoria");
        Objects.requireNonNull(atorTipo, "tipo do ator da mudanca de etapa e obrigatorio");
        Objects.requireNonNull(ocorridoEm, "instante da mudanca de etapa e obrigatorio");
        if (atorTipo == OrigemEvento.USUARIO && atorId == null) {
            throw new IllegalArgumentException("mudanca de etapa por usuario exige atorId");
        }
        if (atorTipo != OrigemEvento.USUARIO && atorId != null) {
            throw new IllegalArgumentException("mudanca de etapa por " + atorTipo + " nao possui usuario");
        }
    }
}
