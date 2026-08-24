package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Evento de dominio publicado apos a transacao; o Redis e apenas entrega, nunca fonte de verdade. */
public final class EventoDeChatInterno {
    private EventoDeChatInterno() {}

    public record MensagemEnviada(UUID conversaId, UUID mensagemId, UUID remetenteId,
            List<UUID> destinatarios, String conteudo, Instant enviadoEm) {}
}
