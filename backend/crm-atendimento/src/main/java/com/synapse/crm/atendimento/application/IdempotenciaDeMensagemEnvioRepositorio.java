package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Índice transacional das tentativas de envio iniciadas pelo navegador. */
public interface IdempotenciaDeMensagemEnvioRepositorio {

    default Optional<Reserva> existente(String chave, UUID usuarioId, UUID leadId) {
        return Optional.empty();
    }

    default Reserva reservar(String chave, UUID usuarioId, UUID leadId, UUID atendimentoId) {
        return new Reserva(chave, usuarioId, leadId, atendimentoId, null, null, false, true);
    }

    default void concluir(
            String chave, UUID usuarioId, UUID mensagemId, Instant enviadoEm, boolean transferiuOLead) {}

    record Reserva(
            String chave,
            UUID usuarioId,
            UUID leadId,
            UUID atendimentoId,
            UUID mensagemId,
            Instant enviadoEm,
            boolean transferiuOLead,
            boolean nova) {}
}
