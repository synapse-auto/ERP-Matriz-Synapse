package com.synapse.crm.core.application.tag;

import java.util.UUID;

/** O contrato interno nao distingue lead inexistente de linha indisponivel pela RLS. */
public class LeadDaAutomacaoNaoEncontradoException extends RuntimeException {

    public LeadDaAutomacaoNaoEncontradoException(UUID leadId) {
        super("Lead " + leadId + " nao encontrado");
    }
}
