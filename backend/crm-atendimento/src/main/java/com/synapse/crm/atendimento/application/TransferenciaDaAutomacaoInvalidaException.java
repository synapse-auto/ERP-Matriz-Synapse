package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** O rodízio da Automação somente entrega conversas que ainda estão sob responsabilidade da IA. */
public class TransferenciaDaAutomacaoInvalidaException extends RuntimeException {

    public TransferenciaDaAutomacaoInvalidaException(UUID atendimentoId) {
        super("atendimento " + atendimentoId + " nao esta sob responsabilidade da IA");
    }
}
