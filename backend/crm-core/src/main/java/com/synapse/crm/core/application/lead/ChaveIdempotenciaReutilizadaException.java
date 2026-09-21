package com.synapse.crm.core.application.lead;

import java.util.UUID;

/** A mesma {@code Idempotency-Key} voltou para outra operacao, outro lead ou outro corpo. */
public class ChaveIdempotenciaReutilizadaException extends RuntimeException {

    public ChaveIdempotenciaReutilizadaException(String chave, String operacao, UUID leadId) {
        super("Idempotency-Key '" + chave + "' ja foi usada para outra requisicao ("
                + operacao + ", lead " + leadId + ")");
    }
}
