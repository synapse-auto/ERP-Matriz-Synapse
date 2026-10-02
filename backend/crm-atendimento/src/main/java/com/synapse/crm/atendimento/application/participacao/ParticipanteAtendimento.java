package com.synapse.crm.atendimento.application.participacao;

import java.time.Instant;
import java.util.UUID;

/**
 * @param origem {@code CONVITE} ou {@code PEDIDO_APROVADO} respondem sem assumir o lead;
 *     {@code ENTRADA_DIRETA} (gestor, Agenda) assume ao enviar (docs/51).
 */
public record ParticipanteAtendimento(UUID usuarioId, String nome, Instant entrouEm, String fotoUrl, String origem) {}
