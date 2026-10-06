package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** Um atendimento aceita um único resultado comercial; repetição idêntica continua idempotente. */
public class ResultadoVendaJaRegistradoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ResultadoVendaJaRegistradoException(UUID atendimentoId) {
        super("O atendimento " + atendimentoId + " já possui resultado de venda registrado.");
    }
}
