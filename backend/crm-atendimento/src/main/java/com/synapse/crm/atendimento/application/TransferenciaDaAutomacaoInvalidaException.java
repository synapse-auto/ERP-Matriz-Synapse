package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** A Automacao somente entrega conversas que ainda estao sob responsabilidade da IA. */
public class TransferenciaDaAutomacaoInvalidaException extends RuntimeException {

    public TransferenciaDaAutomacaoInvalidaException(UUID atendimentoId) {
        super("atendimento " + atendimentoId + " nao esta sob responsabilidade da IA");
    }
}
