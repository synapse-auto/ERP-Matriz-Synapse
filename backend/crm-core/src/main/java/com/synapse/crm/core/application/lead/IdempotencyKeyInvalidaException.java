package com.synapse.crm.core.application.lead;

/** {@code Idempotency-Key} ausente ou em branco num comando de escrita da Automacao. */
public class IdempotencyKeyInvalidaException extends RuntimeException {

    public IdempotencyKeyInvalidaException() {
        super("Idempotency-Key e obrigatoria e nao pode ser vazia");
    }
}
