package com.synapse.crm.atendimento.application.referencia;

import java.time.Instant;
import java.util.UUID;

/** Identidade composta da mensagem que o atendente quer responder. */
public record AlvoDeResposta(UUID mensagemId, Instant enviadoEm) {}
