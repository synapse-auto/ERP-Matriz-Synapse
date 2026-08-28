package com.synapse.crm.atendimento.domain.avaliacao;

/** Nota fora da escala 1–5 gravada no CHECK de {@code avaliacao.nota}. */
public class NotaDeAvaliacaoInvalidaException extends RuntimeException {

    public NotaDeAvaliacaoInvalidaException(int nota) {
        super("nota " + nota + " fora da escala 1 a 5");
    }
}
