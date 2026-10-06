package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** Atendimento classificado como negociacao exige escolha explicita antes da finalizacao manual. */
public class ResultadoVendaObrigatorioException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ResultadoVendaObrigatorioException(UUID atendimentoId) {
        super("O atendimento " + atendimentoId + " exige resultado de venda antes da finalizacao.");
    }
}
