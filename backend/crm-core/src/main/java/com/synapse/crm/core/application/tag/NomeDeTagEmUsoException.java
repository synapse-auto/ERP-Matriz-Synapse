package com.synapse.crm.core.application.tag;

/** Ja existe uma tag com esse nome. Vira 409. */
public class NomeDeTagEmUsoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NomeDeTagEmUsoException(String nome) {
        super("Ja existe uma tag chamada \"" + nome + "\".");
    }
}
