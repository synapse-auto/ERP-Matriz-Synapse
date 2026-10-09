package com.synapse.crm.atendimento.application.informacoeschatbot;

/** A instancia nao habilitou o card de informacoes do chatbot; nada e gravado nem lido. */
public class InformacoesDoChatbotDesabilitadasException extends RuntimeException {

    public InformacoesDoChatbotDesabilitadasException() {
        super("o card de informacoes do chatbot nao esta habilitado nesta instancia");
    }
}
