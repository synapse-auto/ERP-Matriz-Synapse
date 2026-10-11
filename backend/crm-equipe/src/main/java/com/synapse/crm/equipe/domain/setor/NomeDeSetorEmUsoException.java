package com.synapse.crm.equipe.domain.setor;

/** Já existe um setor com este nome (sem diferenciar maiúsculas). */
public class NomeDeSetorEmUsoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NomeDeSetorEmUsoException(String mensagem) {
        super(mensagem);
    }
}
