package com.synapse.crm.sharedkernel.emoji;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmojiUnicodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"👍", "👍🏽", "❤️", "🇧🇷", "1️⃣", "👨‍👩‍👧"})
    void aceitaSequenciasLegitimas(String emoji) {
        assertThat(EmojiUnicode.validar(emoji)).isEqualTo(emoji);
    }

    @Test
    void rejeitaVazio() {
        assertThatThrownBy(() -> EmojiUnicode.validar(""))
                .isInstanceOf(EmojiInvalidoException.class);
        assertThatThrownBy(() -> EmojiUnicode.validar(null))
                .isInstanceOf(EmojiInvalidoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ok", "a", "👍👎", "thumbs"})
    void rejeitaTextoComumOuConcatenacao(String bruto) {
        assertThatThrownBy(() -> EmojiUnicode.validar(bruto))
                .isInstanceOf(EmojiInvalidoException.class);
    }

    @Test
    void rejeitaPayloadGrande() {
        assertThatThrownBy(() -> EmojiUnicode.validar("👍".repeat(20)))
                .isInstanceOf(EmojiInvalidoException.class);
    }
}
