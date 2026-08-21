package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.UUID;

/** Reserva global do identificador da Meta recebido da Automação. */
public interface IdempotenciaDeMensagemAutomacaoRepositorio {

    Reserva reservar(String wamid, UUID atendimentoId, UUID mensagemId, Instant enviadoEm);

    record Reserva(
            String wamid,
            UUID atendimentoId,
            UUID mensagemId,
            Instant enviadoEm,
            boolean nova) {}
}
