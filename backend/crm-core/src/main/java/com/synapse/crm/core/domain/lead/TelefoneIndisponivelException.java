package com.synapse.crm.core.domain.lead;

/** Conflito de identidade de contato, sem revelar outro lead ou seu responsavel. */
public class TelefoneIndisponivelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TelefoneIndisponivelException() {
        super("Nao foi possivel usar esse telefone. Nenhum cadastro foi alterado.");
    }
}
