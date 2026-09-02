package com.synapse.crm.atendimento.domain.avaliacao;

import com.synapse.crm.sharedkernel.avaliacao.EscalaDeAvaliacao;

/** Nota fora da escala 0–10 gravada no CHECK de {@code avaliacao.nota}. */
public class NotaDeAvaliacaoInvalidaException extends RuntimeException {

    public NotaDeAvaliacaoInvalidaException(int nota) {
        super("nota " + nota + " fora da escala "
                + EscalaDeAvaliacao.NOTA_MINIMA + " a " + EscalaDeAvaliacao.NOTA_MAXIMA);
    }
}
