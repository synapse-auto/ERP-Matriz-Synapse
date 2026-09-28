package com.synapse.crm.atendimento.application.template;

/**
 * A variante pedida nao esta entre os templates que o usuario alcanca. Responde como inexistente
 * para nao confirmar que um template restrito existe.
 */
public class TemplateForaDoAlcanceException extends RuntimeException {

    public TemplateForaDoAlcanceException(String id) {
        super("template " + id + " nao encontrado");
    }
}
