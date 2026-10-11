package com.synapse.crm.equipe.domain.setor;

import java.util.UUID;

/** Vínculo de um usuário a um setor; {@code setorId} nulo significa "sem setor". */
public record AtribuicaoDeSetor(UUID usuarioId, UUID setorId) {}
