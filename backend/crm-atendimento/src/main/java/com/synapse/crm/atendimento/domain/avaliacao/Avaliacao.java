package com.synapse.crm.atendimento.domain.avaliacao;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Nota 1–5 do atendimento, atribuida ao atendente dono no instante da coleta.
 *
 * <p>A escala e a do {@code CHECK (nota BETWEEN 1 AND 5)} — o prototipo 0–10 nao entra aqui para
 * nao forcar migracao de dado depois que houver nota real.
 */
public record Avaliacao(
        UUID id,
        UUID atendimentoId,
        UUID atendenteId,
        int nota,
        String comentario,
        Instant criadoEm) {

    public static final int NOTA_MINIMA = 1;
    public static final int NOTA_MAXIMA = 5;

    public Avaliacao {
        Objects.requireNonNull(id, "id da avaliacao e obrigatorio");
        Objects.requireNonNull(atendimentoId, "avaliacao e de um atendimento");
        Objects.requireNonNull(atendenteId, "avaliacao e de um atendente");
        Objects.requireNonNull(criadoEm, "criadoEm e obrigatorio");
        exigirFaixa(nota);
        comentario = normalizar(comentario);
    }

    public static Avaliacao registrar(
            UUID id,
            UUID atendimentoId,
            UUID atendenteId,
            int nota,
            String comentario,
            Instant criadoEm) {
        return new Avaliacao(id, atendimentoId, atendenteId, nota, comentario, criadoEm);
    }

    public static void exigirFaixa(int nota) {
        if (nota < NOTA_MINIMA || nota > NOTA_MAXIMA) {
            throw new NotaDeAvaliacaoInvalidaException(nota);
        }
    }

    private static String normalizar(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        return comentario.trim();
    }
}
