package com.synapse.crm.equipe.application.autenticacao;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.synapse.crm.equipe.domain.sessao.TokenOpaco;
import com.synapse.crm.equipe.domain.usuario.Usuario;

/**
 * Emite um par de tokens e registra o refresh.
 *
 * <p>Login e renovacao emitem sessao do mesmo jeito; a diferenca esta so na familia (nova no login,
 * herdada na renovacao). Concentrar aqui evita que as duas se afastem — e "o refresh do login era
 * gravado diferente do refresh da renovacao" e o tipo de divergencia que so aparece em producao.
 */
@Service
public class EmissorDeSessao {

    private final RefreshTokenRepositorio refreshTokens;
    private final GeradorDeAccessToken accessTokens;
    private final PoliticaDeSessao politica;
    private final Clock relogio;

    public EmissorDeSessao(
            RefreshTokenRepositorio refreshTokens,
            GeradorDeAccessToken accessTokens,
            PoliticaDeSessao politica,
            Clock relogio) {
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.politica = politica;
        this.relogio = relogio;
    }

    public Sessao emitir(Usuario usuario, UUID familia) {
        String refresh = TokenOpaco.gerar();
        refreshTokens.salvar(
                usuario.id(),
                familia,
                TokenOpaco.hash(refresh),
                relogio.instant().plus(politica.validadeDoRefreshToken()));

        return new Sessao(
                accessTokens.gerar(usuario.comoAutenticado()),
                refresh,
                accessTokens.validade().toSeconds());
    }
}
