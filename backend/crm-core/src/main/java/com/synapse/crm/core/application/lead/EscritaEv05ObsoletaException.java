package com.synapse.crm.core.application.lead;

import java.util.UUID;

public class EscritaEv05ObsoletaException extends RuntimeException {
    public EscritaEv05ObsoletaException(UUID leadId) {
        super("ciclo EV-05 obsoleto ou lead inexistente: " + leadId);
    }
}
