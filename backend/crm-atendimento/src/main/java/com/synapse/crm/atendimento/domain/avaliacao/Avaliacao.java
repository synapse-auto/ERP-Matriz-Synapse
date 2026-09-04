package com.synapse.crm.atendimento.domain.avaliacao;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.synapse.crm.sharedkernel.avaliacao.EscalaDeAvaliacao;

/**
 * Nota 0–10 do atendimento, atribuida ao atendente dono no instante da coleta.
 *
 * <p>A escala e a mesma do {@code CHECK (nota BETWEEN 0 AND 10)} e do contrato EV-08 (n8n):
 * Ruim = 2, Bom = 7, Otimo = 10. Constantes canonicas em {@link EscalaDeAvaliacao}.
 */
public record Avaliacao(
        UUID id,
        UUID atendimentoId,
        UUID atendenteId,
        int nota,
        String comentario,
        Instant criadoEm) {

    public static final int NOTA_MINIMA = EscalaDeAvaliacao.NOTA_MINIMA;
    public static final int NOTA_MAXIMA = EscalaDeAvaliacao.NOTA_MAXIMA;

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

    public boolean ehIdentica(int outraNota, String outroComentario) {
        return this.nota == outraNota && Objects.equals(this.comentario, normalizar(outroComentario));
    }

    public static String normalizar(String comentario) {
        if (comentario == null || comentario.isBlank()) {
            return null;
        }
        return comentario.trim();
    }
}
