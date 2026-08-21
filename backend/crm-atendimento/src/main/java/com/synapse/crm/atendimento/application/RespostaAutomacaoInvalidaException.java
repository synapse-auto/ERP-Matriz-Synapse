package com.synapse.crm.atendimento.application;

/** O workflow não pode responder por cima de um atendimento humano ou finalizado. */
public class RespostaAutomacaoInvalidaException extends RuntimeException {
    public RespostaAutomacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
