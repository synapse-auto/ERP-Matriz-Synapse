package com.synapse.crm.equipe.application.autenticacao;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.sessao.TokenOpaco;

/**
 * Logout: revoga a familia do refresh apresentado.
 *
 * <p>Revoga a familia, e nao so o token, para que nenhum sucessor emitido antes continue valendo.
 *
 * <p>Idempotente e silencioso: token desconhecido nao vira erro. Logout que falha so ensina o
 * usuario a ignorar o botao, e responder "esse token nao existe" confirmaria a um atacante quais
 * tokens sao validos.
 */
@Service
public class EncerrarSessaoUseCase {

    private final RefreshTokenRepositorio refreshTokens;

    public EncerrarSessaoUseCase(RefreshTokenRepositorio refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public void executar(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokens
                .porHash(TokenOpaco.hash(refreshToken))
                .ifPresent(token -> refreshTokens.revogarFamilia(token.familia()));
    }
}
