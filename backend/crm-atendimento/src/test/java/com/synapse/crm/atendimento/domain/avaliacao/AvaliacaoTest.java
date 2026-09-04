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
                10,
                "   ",
                Instant.parse("2026-08-28T13:00:00Z"));

        assertThat(avaliacao.nota()).isEqualTo(10);
        assertThat(avaliacao.comentario()).isNull();
    }

    @Test
    void registrar_limites0e10_saoAceitos() {
        assertThat(Avaliacao.registrar(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        0,
                        null,
                        Instant.parse("2026-08-28T13:00:00Z"))
                .nota())
                .isZero();
        assertThat(Avaliacao.registrar(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        10,
                        null,
                        Instant.parse("2026-08-28T13:00:00Z"))
                .nota())
                .isEqualTo(10);
    }

    @Test
    void registrar_nota6_passaASerValidaNaEscala0a10() {
        // Antes da E128, 6 era invalido (faixa 1–5). Agora e o "Bom" do EV-08.
        assertThat(Avaliacao.registrar(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        6,
                        null,
                        Instant.parse("2026-08-28T13:00:00Z"))
                .nota())
                .isEqualTo(6);
    }

    @Test
    void registrar_notaForaDaFaixa_falha() {
        assertThatThrownBy(() -> Avaliacao.registrar(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        -1,
                        null,
                        Instant.parse("2026-08-28T13:00:00Z")))
                .isInstanceOf(NotaDeAvaliacaoInvalidaException.class)
                .hasMessageContaining("-1");
        assertThatThrownBy(() -> Avaliacao.registrar(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        11,
                        null,
                        Instant.parse("2026-08-28T13:00:00Z")))
                .isInstanceOf(NotaDeAvaliacaoInvalidaException.class)
                .hasMessageContaining("11");
    }

    @Test
    void ehIdentica_mesmaNotaEComentario_retornaTrue() {
        Avaliacao comComentario = Avaliacao.registrar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                8,
                "atendimento rapido",
                Instant.parse("2026-08-28T13:00:00Z"));

        assertThat(comComentario.ehIdentica(8, "atendimento rapido")).isTrue();
        assertThat(comComentario.ehIdentica(8, "  atendimento rapido  ")).isTrue();

        Avaliacao semComentario = Avaliacao.registrar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                5,
                null,
                Instant.parse("2026-08-28T13:00:00Z"));

        assertThat(semComentario.ehIdentica(5, null)).isTrue();
        assertThat(semComentario.ehIdentica(5, "")).isTrue();
        assertThat(semComentario.ehIdentica(5, "   ")).isTrue();
    }

    @Test
    void ehIdentica_notaOuComentarioDivergentes_retornaFalse() {
        Avaliacao avaliacao = Avaliacao.registrar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                7,
                "bom",
                Instant.parse("2026-08-28T13:00:00Z"));

        assertThat(avaliacao.ehIdentica(8, "bom")).as("nota diferente").isFalse();
        assertThat(avaliacao.ehIdentica(7, "otimo")).as("comentario diferente").isFalse();
        assertThat(avaliacao.ehIdentica(7, null)).as("comentario ausente").isFalse();
        assertThat(avaliacao.ehIdentica(7, "")).as("comentario em branco").isFalse();
    }

    @Test
    void normalizar_trataNullVazioEEspacos() {
        assertThat(Avaliacao.normalizar(null)).isNull();
        assertThat(Avaliacao.normalizar("")).isNull();
        assertThat(Avaliacao.normalizar("   ")).isNull();
        assertThat(Avaliacao.normalizar("  texto valido  ")).isEqualTo("texto valido");
    }
}
