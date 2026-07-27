package com.synapse.crm.equipe.application.autenticacao;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.autenticacao.RefreshTokenRepositorio.RefreshTokenArmazenado;
import com.synapse.crm.equipe.domain.sessao.TokenOpaco;
import com.synapse.crm.equipe.domain.usuario.AutenticacaoInvalidaException;
import com.synapse.crm.equipe.domain.usuario.Usuario;

/**
 * Renova a sessao trocando o refresh token por um par novo — rotacao.
 *
 * <p>Cada refresh vale uma vez so. Apresentar de novo um token ja usado significa que ou o cliente
 * repetiu a chamada, ou alguem copiou o token: como nao da para distinguir os dois, tratamos pelo
 * pior caso e derrubamos a familia inteira daquele login. O usuario legitimo precisa entrar de
 * novo; o ladrao perde o acesso junto.
 */
@Service
public class RenovarSessaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(RenovarSessaoUseCase.class);

    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final EmissorDeSessao emissor;
    private final Clock relogio;

    public RenovarSessaoUseCase(
            UsuarioRepositorio usuarios,
            RefreshTokenRepositorio refreshTokens,
            EmissorDeSessao emissor,
            Clock relogio) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.emissor = emissor;
        this.relogio = relogio;
    }

    /**
     * O {@code noRollbackFor} nao e detalhe de configuracao.
     *
     * <p>As revogacoes deste metodo acontecem junto com a recusa: detectamos reuso, revogamos a
     * familia e lancamos. Com o rollback padrao, o lancamento desfaria a revogacao e o token roubado
     * continuaria valendo — a deteccao de reuso viraria enfeite, e o teste passaria pela metade
     * certa (a chamada recusada) escondendo a metade errada (a familia intacta).
     */
    @Transactional(noRollbackFor = AutenticacaoInvalidaException.class)
    public Sessao executar(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AutenticacaoInvalidaException.sessaoInvalida();
        }

        RefreshTokenArmazenado armazenado = refreshTokens
                .porHash(TokenOpaco.hash(refreshToken))
                .orElseThrow(AutenticacaoInvalidaException::sessaoInvalida);

        if (armazenado.estaRevogado()) {
            // Reuso: pelo pior caso, o token vazou. Derruba a cadeia inteira.
            log.warn(
                    "Refresh token reutilizado (usuario={}, familia={}). Revogando a familia inteira.",
                    armazenado.usuarioId(),
                    armazenado.familia());
            refreshTokens.revogarFamilia(armazenado.familia());
            throw AutenticacaoInvalidaException.sessaoInvalida();
        }

        if (armazenado.estaExpirado(relogio.instant())) {
            refreshTokens.revogar(armazenado.id());
            throw AutenticacaoInvalidaException.sessaoInvalida();
        }

        Usuario usuario = usuarios
                .porId(armazenado.usuarioId())
                .orElseThrow(AutenticacaoInvalidaException::sessaoInvalida);

        // Desativar um usuario precisa cortar a sessao dele tambem, nao so o login.
        if (!usuario.ativo()) {
            refreshTokens.revogarFamilia(armazenado.familia());
            throw AutenticacaoInvalidaException.usuarioInativo();
        }

        // Rotacao: o token apresentado morre aqui, e o sucessor herda a familia.
        refreshTokens.revogar(armazenado.id());
        return emissor.emitir(usuario, armazenado.familia());
    }
}
