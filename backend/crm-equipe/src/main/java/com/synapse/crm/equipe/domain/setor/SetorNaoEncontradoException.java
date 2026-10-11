package com.synapse.crm.equipe.domain.setor;

/** Setor não encontrado. */
public class SetorNaoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SetorNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
