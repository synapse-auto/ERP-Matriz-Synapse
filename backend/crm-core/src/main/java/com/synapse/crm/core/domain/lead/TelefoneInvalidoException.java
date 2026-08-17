package com.synapse.crm.core.domain.lead;

/** Entrada presente, mas curta demais para representar um telefone discavel. */
public class TelefoneInvalidoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public TelefoneInvalidoException() {
        super("Telefone invalido: informe DDD e numero com 10 ou 11 digitos, ou inclua o DDI");
    }
}
