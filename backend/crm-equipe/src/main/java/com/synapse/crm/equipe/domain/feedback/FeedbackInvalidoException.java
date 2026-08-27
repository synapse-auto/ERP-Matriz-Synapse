package com.synapse.crm.equipe.domain.feedback;

public class FeedbackInvalidoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FeedbackInvalidoException(String mensagem) {
        super(mensagem);
    }
}
