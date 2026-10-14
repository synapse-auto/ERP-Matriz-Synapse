package com.synapse.crm.equipe.domain.usuario;

import java.util.UUID;

/** Fato persistido; consumidores reagem somente depois do commit. */
public record PresencaDoUsuarioAlterada(UUID usuarioId) {}
