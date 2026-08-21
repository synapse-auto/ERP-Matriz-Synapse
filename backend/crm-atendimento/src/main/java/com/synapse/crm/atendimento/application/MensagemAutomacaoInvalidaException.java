package com.synapse.crm.atendimento.application;

/** Corpo normalizado da Automação não representa uma mensagem válida. */
public class MensagemAutomacaoInvalidaException extends RuntimeException {

    public MensagemAutomacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
