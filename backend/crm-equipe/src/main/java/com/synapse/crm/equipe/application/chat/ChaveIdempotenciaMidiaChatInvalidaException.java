package com.synapse.crm.equipe.application.chat;

/** A mesma chave não pode representar outro upload, usuário ou conversa. */
public class ChaveIdempotenciaMidiaChatInvalidaException extends RuntimeException {
    public ChaveIdempotenciaMidiaChatInvalidaException() {
        super("Idempotency-Key já utilizada para outro envio de mídia");
    }
}
