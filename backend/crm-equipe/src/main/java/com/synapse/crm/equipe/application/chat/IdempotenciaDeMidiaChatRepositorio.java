package com.synapse.crm.equipe.application.chat;

import java.util.Optional;
import java.util.UUID;

/** Reserva transacional de tentativas de upload de mídia no chat interno. */
public interface IdempotenciaDeMidiaChatRepositorio {

    Reserva reservar(String chave, UUID remetenteId, UUID conversaId, String impressao);

    void concluir(String chave, UUID mensagemId);

    void cancelar(String chave);

    record Reserva(
            String chave,
            UUID remetenteId,
            UUID conversaId,
            String impressao,
            UUID mensagemId,
            boolean nova) {

        public Optional<UUID> mensagemConcluida() {
            return Optional.ofNullable(mensagemId);
        }
    }
}
