package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** Resultado informado na finalização só se aplica a atendimentos em negociação. */
public class ResultadoVendaIncompativelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ResultadoVendaIncompativelException(UUID atendimentoId) {
        super("O atendimento " + atendimentoId + " não está classificado como negociação.");
    }
}
