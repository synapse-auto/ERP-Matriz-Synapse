package com.synapse.crm.equipe.application.chat;

/** A mensagem solicitada não pertence à conversa autorizada ou não existe. */
public class MensagemChatInternoNaoEncontradaException extends RuntimeException {
    public MensagemChatInternoNaoEncontradaException() {
        super("Mensagem nao encontrada na conversa.");
    }
}
