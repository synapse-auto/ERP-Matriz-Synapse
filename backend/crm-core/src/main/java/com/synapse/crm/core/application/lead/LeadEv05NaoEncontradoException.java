package com.synapse.crm.core.application.lead;

import java.util.UUID;

public class LeadEv05NaoEncontradoException extends RuntimeException {
    public LeadEv05NaoEncontradoException(UUID leadId) {
        super("lead nao encontrado para o ciclo EV-05: " + leadId);
    }
}
