package com.synapse.crm.automacaoconfig.domain.regras;

import java.util.UUID;

/** Uma regra de fidelizacao ({@code regra_fidelizacao}, V7) — Java puro. */
public record RegraFidelizacao(UUID id, int diasSemContato, String mensagem, boolean ativo) {}
