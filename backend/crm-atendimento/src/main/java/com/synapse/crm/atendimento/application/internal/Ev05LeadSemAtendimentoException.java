package com.synapse.crm.atendimento.application.internal;

import java.util.UUID;

public class Ev05LeadSemAtendimentoException extends RuntimeException {
    public Ev05LeadSemAtendimentoException(UUID leadId) {
        super("lead sem atendimento EM_ATENDIMENTO elegivel: " + leadId);
    }
}
