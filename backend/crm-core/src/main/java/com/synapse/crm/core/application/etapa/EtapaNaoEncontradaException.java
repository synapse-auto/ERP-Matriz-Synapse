package com.synapse.crm.core.application.etapa;

import java.util.UUID;

/** Etapa informada numa transicao nao existe na configuracao atual do funil. */
public class EtapaNaoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EtapaNaoEncontradaException(UUID id) {
        super("Etapa nao encontrada: " + id);
    }
}
