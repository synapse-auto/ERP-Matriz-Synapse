package com.synapse.crm.atendimento.application;

/** Nao existe destino elegivel para a Automacao neste momento. */
public class NenhumAtendenteDisponivelException extends RuntimeException {

    public NenhumAtendenteDisponivelException() {
        super("nenhum atendente esta online e disponivel para receber a conversa");
    }
}
