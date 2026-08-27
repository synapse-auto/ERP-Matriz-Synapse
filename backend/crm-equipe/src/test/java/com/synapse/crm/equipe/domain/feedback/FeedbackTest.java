package com.synapse.crm.equipe.domain.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FeedbackTest {
    @Test
    void normalizaDescricaoSemPerderConteudo() {
        Feedback feedback = new Feedback(UUID.randomUUID(), UUID.randomUUID(),
                TipoFeedback.SUGESTAO, AreaFeedback.GERAL, "  Melhorar a busca  ", Instant.EPOCH);

        assertThat(feedback.descricao()).isEqualTo("Melhorar a busca");
    }

    @Test
    void recusaDescricaoVaziaOuAcimaDoLimite() {
        assertThatThrownBy(() -> novo("   ")).isInstanceOf(FeedbackInvalidoException.class);
        assertThatThrownBy(() -> novo("x".repeat(Feedback.LIMITE_DESCRICAO + 1)))
                .isInstanceOf(FeedbackInvalidoException.class);
    }

    private static Feedback novo(String descricao) {
        return new Feedback(UUID.randomUUID(), UUID.randomUUID(), TipoFeedback.ERRO,
                AreaFeedback.ATENDIMENTOS, descricao, Instant.EPOCH);
    }
}
