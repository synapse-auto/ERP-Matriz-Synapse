package com.synapse.crm.equipe.domain.setor;

/** O setor está inativo e não aceita novos integrantes. */
public class SetorInativoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SetorInativoException(String mensagem) {
        super(mensagem);
    }
}
