package com.synapse.crm.equipe.application.autenticacao;

import java.time.Duration;

import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/**
 * Porta de emissao do access token.
 *
 * <p>A aplicacao nao sabe que e JWT: trocar o formato do token e trocar o adaptador, nao os casos de
 * uso.
 */
public interface GeradorDeAccessToken {

    String gerar(UsuarioAutenticado usuario);

    Duration validade();
}
