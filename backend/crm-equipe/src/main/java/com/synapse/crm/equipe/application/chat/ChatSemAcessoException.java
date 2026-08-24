package com.synapse.crm.equipe.application.chat;

/** Recurso do chat que nao pertence ao usuario da requisicao. */
public class ChatSemAcessoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ChatSemAcessoException() {
        super("Usuario nao participa desta conversa.");
    }
}
