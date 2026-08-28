package com.synapse.crm.atendimento.domain.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AvaliacaoTest {

    @Test
    void registrar_notaNaFaixa_normalizaComentarioVazio() {
        Avaliacao avaliacao = Avaliacao.registrar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                5,
                "   ",
                Instant.parse("2026-08-28T13:00:00Z"));

        assertThat(avaliacao.nota()).isEqualTo(5);
        assertThat(avaliacao.comentario()).isNull();
    }

    @Test
    void registrar_notaForaDaFaixa_falha() {
        assertThatThrownBy(() -> Avaliacao.registrar(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        6,
                        null,
                        Instant.parse("2026-08-28T13:00:00Z")))
                .isInstanceOf(NotaDeAvaliacaoInvalidaException.class)
                .hasMessageContaining("6");
    }
}
