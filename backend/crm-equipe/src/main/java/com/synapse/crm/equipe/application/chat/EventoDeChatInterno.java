package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

/** Evento publicado apos o commit; o Redis so entrega, nunca e fonte de verdade. */
public final class EventoDeChatInterno {
    private EventoDeChatInterno() {}

    public record MensagemEnviada(UUID conversaId, UUID mensagemId, UUID remetenteId,
            List<UUID> destinatarios, String conteudo, Instant enviadoEm) {}

    public record ReacaoAlterada(
            UUID conversaId,
            UUID mensagemId,
            UUID atorId,
            String emojiDoAtor,
            List<UUID> destinatarios,
            List<ResumoDeReacao> reacoes) {
        public ReacaoAlterada {
            destinatarios = destinatarios == null ? List.of() : List.copyOf(destinatarios);
            reacoes = reacoes == null ? List.of() : List.copyOf(reacoes);
        }
    }
}
