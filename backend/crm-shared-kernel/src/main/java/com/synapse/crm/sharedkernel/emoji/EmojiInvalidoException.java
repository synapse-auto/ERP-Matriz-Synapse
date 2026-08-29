package com.synapse.crm.sharedkernel.emoji;

/** Payload que nao e um unico emoji Unicode. Vira 400 no ponto de entrada HTTP. */
public class EmojiInvalidoException extends RuntimeException {

    public EmojiInvalidoException(String detalhe) {
        super("Emoji de reacao invalido: " + detalhe);
    }
}
