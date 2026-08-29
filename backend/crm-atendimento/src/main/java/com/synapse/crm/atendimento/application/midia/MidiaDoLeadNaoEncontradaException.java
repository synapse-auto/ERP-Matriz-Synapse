package com.synapse.crm.atendimento.application.midia;

import java.util.UUID;

public class MidiaDoLeadNaoEncontradaException extends RuntimeException {
    public MidiaDoLeadNaoEncontradaException(UUID leadId, UUID mensagemId) {
        super("Mídia não encontrada para o lead " + leadId + " e mensagem " + mensagemId);
    }
}
