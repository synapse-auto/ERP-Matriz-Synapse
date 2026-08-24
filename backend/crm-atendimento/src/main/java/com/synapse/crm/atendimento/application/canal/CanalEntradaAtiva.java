package com.synapse.crm.atendimento.application.canal;

import java.util.UUID;

/** Identidade persistida do canal e da credencial que recebeu o webhook. */
public record CanalEntradaAtiva(UUID canalId, UUID canalCredencialId) {}
