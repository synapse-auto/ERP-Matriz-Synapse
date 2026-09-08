package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

/** Mídia inexistente ou que não pertence à conversa informada. */
public class MidiaChatInternoNaoEncontradaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MidiaChatInternoNaoEncontradaException(UUID conversaId, UUID mensagemId) {
        super("Mídia não encontrada na conversa " + conversaId + ": " + mensagemId);
    }
}
