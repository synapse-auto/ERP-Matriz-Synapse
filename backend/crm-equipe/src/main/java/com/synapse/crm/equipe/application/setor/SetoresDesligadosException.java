package com.synapse.crm.equipe.application.setor;

/**
 * A feature flag {@code setores} está desligada nesta instância: o recurso não existe para ela. Vira 404, o mesmo
 * que o sistema respondia antes de existirem setores.
 */
public class SetoresDesligadosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SetoresDesligadosException() {
        super("Setores não estão habilitados nesta instância.");
    }
}
