package com.synapse.crm.equipe.domain.disponibilidade;

import java.util.UUID;

/**
 * Um atendente que marcou {@code disponibilidade_atendente_ia.disponivel_para_ia = true} — quem a
 * Automacao pode escolher ao rotear um lead sem dono (E07 §1: {@code GET
 * /internal/v1/atendentes/disponiveis}).
 */
public record AtendenteDisponivelParaIa(UUID usuarioId, String nome, String email) {}
