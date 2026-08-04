package com.synapse.crm.atendimento.application.canal;

import java.util.UUID;

/** Metadados publicos de um canal, sem credencial nem referencia de segredo. */
public record CanalResumo(UUID id, String nome, String tipo, boolean ativo) {}
