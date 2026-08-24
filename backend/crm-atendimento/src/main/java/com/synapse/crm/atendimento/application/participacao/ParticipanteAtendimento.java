package com.synapse.crm.atendimento.application.participacao;

import java.time.Instant;
import java.util.UUID;

public record ParticipanteAtendimento(UUID usuarioId, String nome, Instant entrouEm) {}
