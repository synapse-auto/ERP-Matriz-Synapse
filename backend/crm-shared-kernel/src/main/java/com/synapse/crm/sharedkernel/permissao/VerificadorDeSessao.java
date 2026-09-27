package com.synapse.crm.sharedkernel.permissao;

import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * O papel que um token carrega ainda e o papel atual, e o usuario continua ativo? Usado onde a
 * cadeia HTTP de filtros nao passa — o handshake do WebSocket.
 */
public interface VerificadorDeSessao {

    boolean vigente(UUID usuarioId, PapelUsuario papelDoToken);
}
