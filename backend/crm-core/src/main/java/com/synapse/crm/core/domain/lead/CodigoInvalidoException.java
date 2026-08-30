package com.synapse.crm.core.domain.lead;

/** Entrada presente, mas nao e um codigo somente de digitos dentro do tamanho do schema. */
public class CodigoInvalidoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public CodigoInvalidoException() {
        super("Codigo invalido: informe apenas numeros, com no maximo 20 digitos");
    }
}
