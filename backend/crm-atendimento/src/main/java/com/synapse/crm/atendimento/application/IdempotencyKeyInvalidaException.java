package com.synapse.crm.atendimento.application;

/** A escrita interna sem chave não pode ser confundida com uma operação segura para retry. */
public class IdempotencyKeyInvalidaException extends RuntimeException {
    public IdempotencyKeyInvalidaException() {
        super("Idempotency-Key e obrigatorio");
    }
}
