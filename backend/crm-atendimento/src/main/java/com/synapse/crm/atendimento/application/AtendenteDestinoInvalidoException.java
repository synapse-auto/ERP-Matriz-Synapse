package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** O contrato interno só aceita atendente ativo, nunca gestor, subgestor ou IA. */
public class AtendenteDestinoInvalidoException extends RuntimeException {
    public AtendenteDestinoInvalidoException(UUID atendenteId) {
        super("destino nao e um atendente ativo: " + atendenteId);
    }
}
