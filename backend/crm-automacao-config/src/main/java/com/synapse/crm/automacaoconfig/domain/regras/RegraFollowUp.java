package com.synapse.crm.automacaoconfig.domain.regras;

import java.util.UUID;

/** Uma regra de follow-up ({@code regra_follow_up}, V7) — Java puro. */
public record RegraFollowUp(UUID id, String nome, int tempoMinutos, String texto, boolean ativo) {}
