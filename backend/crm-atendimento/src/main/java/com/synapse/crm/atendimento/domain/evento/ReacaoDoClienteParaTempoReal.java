package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Reacao do cliente persistida, pronta para a bolha (E214). {@code emoji} nulo = o cliente removeu.
 * Sem telefone nem nome: a tela ja sabe de quem e a conversa.
 */
public record ReacaoDoClienteParaTempoReal(
        UUID atendimentoId, UUID mensagemId, Instant enviadoEm, String emoji) {}
