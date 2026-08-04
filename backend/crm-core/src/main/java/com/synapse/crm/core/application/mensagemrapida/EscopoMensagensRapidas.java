package com.synapse.crm.core.application.mensagemrapida;

import java.util.UUID;

/** Specification explicita da lista pessoal: gestor pode ampliar somente na tela de gestao. */
public record EscopoMensagensRapidas(UUID usuarioId, boolean todas) {}
