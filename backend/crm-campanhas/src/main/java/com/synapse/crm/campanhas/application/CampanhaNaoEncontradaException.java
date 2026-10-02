package com.synapse.crm.campanhas.application;

import java.util.UUID;

/** A campanha nao existe (ou a RLS a esconde de quem pediu). */
public class CampanhaNaoEncontradaException extends RuntimeException {

    public CampanhaNaoEncontradaException(UUID id) {
        super("campanha nao encontrada: " + id);
    }
}
