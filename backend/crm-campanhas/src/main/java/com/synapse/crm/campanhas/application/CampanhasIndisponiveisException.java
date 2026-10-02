package com.synapse.crm.campanhas.application;

/**
 * Campanhas nao estao disponiveis nesta instancia: a funcionalidade esta desligada ou o canal ativo nao
 * administra templates (somente o provedor oficial da Meta). A API responde como se o recurso nao existisse.
 */
public class CampanhasIndisponiveisException extends RuntimeException {

    public CampanhasIndisponiveisException() {
        super("campanhas nao estao disponiveis nesta instancia");
    }
}
