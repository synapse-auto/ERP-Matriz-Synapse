package com.synapse.crm.relatorios.application.cancelamentos;

public final class ConflitoDeCancelamentoException extends RuntimeException {
    public ConflitoDeCancelamentoException() { super("Evento ou chave de idempotencia reutilizado com dados diferentes."); }
}
