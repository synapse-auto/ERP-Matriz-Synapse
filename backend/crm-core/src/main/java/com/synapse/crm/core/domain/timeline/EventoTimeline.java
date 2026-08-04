package com.synapse.crm.core.domain.timeline;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Fato imutavel e append-only da historia de um lead. */
public record EventoTimeline(
        UUID id,
        UUID leadId,
        UUID atendimentoId,
        String tipo,
        String descricao,
        OrigemEvento origem,
        UUID atorId,
        Map<String, Object> dados,
        Instant criadoEm) {

    public EventoTimeline {
        Objects.requireNonNull(id, "id do evento e obrigatorio");
        Objects.requireNonNull(leadId, "lead do evento e obrigatorio");
        Objects.requireNonNull(tipo, "tipo do evento e obrigatorio");
        Objects.requireNonNull(descricao, "descricao do evento e obrigatoria");
        Objects.requireNonNull(origem, "origem do evento e obrigatoria");
        Objects.requireNonNull(dados, "dados estruturados sao obrigatorios");
        dados = Collections.unmodifiableMap(new LinkedHashMap<>(dados));
        Objects.requireNonNull(criadoEm, "instante do evento e obrigatorio");
    }
}
