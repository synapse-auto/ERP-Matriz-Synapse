package com.synapse.crm.atendimento.application.participacao;

import java.time.Instant;
import java.util.UUID;

public record PedidoEntradaAtendimento(UUID id, UUID atendimentoId, UUID solicitanteId,
        String solicitanteNome, StatusPedidoEntrada status, Instant solicitadoEm) {}
