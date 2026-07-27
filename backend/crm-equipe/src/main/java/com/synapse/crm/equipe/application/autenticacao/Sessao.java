package com.synapse.crm.equipe.application.autenticacao;

/**
 * Par de tokens devolvido no login e na renovacao.
 *
 * @param accessToken curto, usado em toda requisicao
 * @param refreshToken longo e opaco; muda a cada renovacao (rotacao)
 * @param expiraEmSegundos validade do access token
 */
public record Sessao(String accessToken, String refreshToken, long expiraEmSegundos) {}
