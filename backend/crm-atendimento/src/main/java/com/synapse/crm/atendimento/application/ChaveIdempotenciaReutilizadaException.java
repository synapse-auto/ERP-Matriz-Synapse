package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** A mesma chave não pode representar outro comando ou outro atendimento. */
public class ChaveIdempotenciaReutilizadaException extends RuntimeException {
    public ChaveIdempotenciaReutilizadaException(String chave, String operacao, UUID atendimentoId) {
        super("Idempotency-Key ja utilizada para outra operacao ou atendimento: " + chave);
    }
}
