package com.synapse.crm.equipe.domain.feedback;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Feedback(UUID id, UUID autorId, TipoFeedback tipo, AreaFeedback area,
        String descricao, Instant criadoEm) {

    public static final int LIMITE_DESCRICAO = 2000;

    public Feedback {
        Objects.requireNonNull(id, "id é obrigatório");
        Objects.requireNonNull(autorId, "autor é obrigatório");
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(area, "área é obrigatória");
        Objects.requireNonNull(criadoEm, "data de criação é obrigatória");
        descricao = descricao == null ? "" : descricao.trim();
        if (descricao.isEmpty()) {
            throw new FeedbackInvalidoException("A descrição do feedback é obrigatória.");
        }
        if (descricao.length() > LIMITE_DESCRICAO) {
            throw new FeedbackInvalidoException(
                    "A descrição do feedback deve ter no máximo " + LIMITE_DESCRICAO + " caracteres.");
        }
    }
}
