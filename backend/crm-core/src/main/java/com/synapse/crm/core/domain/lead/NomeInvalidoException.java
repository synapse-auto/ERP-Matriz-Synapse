package com.synapse.crm.core.domain.lead;

/** Entrada presente, mas o nome ficou vazio ou passou do tamanho do schema. */
public class NomeInvalidoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public NomeInvalidoException() {
        super("Nome invalido: informe o nome do cliente, com no maximo 150 caracteres");
    }
}
