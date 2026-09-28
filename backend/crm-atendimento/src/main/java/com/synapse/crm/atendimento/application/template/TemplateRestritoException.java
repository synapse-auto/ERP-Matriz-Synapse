package com.synapse.crm.atendimento.application.template;

/** O papel atual nao pode usar um template cujo nome e reservado a administradores. */
public class TemplateRestritoException extends RuntimeException {

    public TemplateRestritoException() {
        super("template reservado a administradores");
    }
}
