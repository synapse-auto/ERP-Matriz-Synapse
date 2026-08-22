package com.synapse.crm.equipe.application.autenticacao;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.SenhaInvalidaException;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Troca a propria senha (E29) — vale tanto para o primeiro acesso quanto para a troca voluntaria; o
 * fluxo e identico, so muda o motivo pelo qual o usuario chegou aqui.
 *
 * <p>Exige a senha atual mesmo no primeiro acesso: e o que impede que um token vazado troque a
 * senha do dono sem saber a senha original. O alvo e sempre {@code usuarioContext.atual()} — nunca
 * um id vindo de fora — porque esta rota nao e de administracao, e de autoatendimento.
 *
 * <p>Revoga todas as demais sessoes (a familia de refresh token) porque trocar a senha sem derrubar
 * as outras deixaria em pe exatamente a sessao de quem o dono quer excluir. A sessao que fez a
 * troca continua valida na pratica: este metodo emite um par de tokens NOVO, com a claim
 * {@code senha_provisoria} ja refletindo o estado atualizado — o token antigo, que ainda carregava
 * {@code senha_provisoria=true}, e descartado pelo proprio chamador (o frontend adota a resposta
 * deste endpoint), nao porque o refresh antigo tenha sido preservado.
 */
@Service
public class AlterarSenhaUseCase {

    private final UsuarioRepositorio usuarios;
    private final CodificadorDeSenha senhas;
    private final PoliticaDeSenha politica;
    private final RefreshTokenRepositorio refreshTokens;
    private final EmissorDeSessao emissor;
    private final UsuarioContext usuarioContext;
    private final Clock relogio;

    public AlterarSenhaUseCase(
            UsuarioRepositorio usuarios,
            CodificadorDeSenha senhas,
            PoliticaDeSenha politica,
            RefreshTokenRepositorio refreshTokens,
            EmissorDeSessao emissor,
            UsuarioContext usuarioContext,
            Clock relogio) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.politica = politica;
        this.refreshTokens = refreshTokens;
        this.emissor = emissor;
        this.usuarioContext = usuarioContext;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "TROCAR_SENHA", entidadeTipo = "USUARIO")
    public Sessao executar(String senhaAtual, String novaSenha) {
        UUID usuarioId = usuarioContext.atual().id();
        Usuario usuario = usuarios.porId(usuarioId).orElseThrow(SenhaInvalidaException::atualIncorreta);

        if (!senhas.confere(senhaAtual, usuario.senhaHash())) {
            throw SenhaInvalidaException.atualIncorreta();
        }
        if (novaSenha == null || novaSenha.length() < politica.tamanhoMinimo()) {
            throw SenhaInvalidaException.foraDaPolitica(politica.tamanhoMinimo());
        }
        if (senhas.confere(novaSenha, usuario.senhaHash())) {
            throw SenhaInvalidaException.igualAAtual();
        }

        String novoHash = senhas.codificar(novaSenha);
        Instant agora = relogio.instant();
        usuarios.atualizarSenha(usuarioId, novoHash, agora);
        refreshTokens.revogarTodosDoUsuario(usuarioId);

        Usuario atualizado = new Usuario(
                usuario.id(), usuario.nome(), usuario.email(), novoHash, usuario.papel(),
                usuario.statusPresenca(), usuario.ativo(), usuario.disponivelParaIa(), agora);
        return emissor.emitir(atualizado, UUID.randomUUID());
    }
}
