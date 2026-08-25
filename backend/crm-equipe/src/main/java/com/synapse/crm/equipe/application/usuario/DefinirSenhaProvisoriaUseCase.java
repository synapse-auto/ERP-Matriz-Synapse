package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.autenticacao.CodificadorDeSenha;
import com.synapse.crm.equipe.application.autenticacao.PoliticaDeSenha;
import com.synapse.crm.equipe.application.autenticacao.RefreshTokenRepositorio;
import com.synapse.crm.equipe.domain.usuario.GeradorDeSenhaAleatoria;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/**
 * O gestor devolve o acesso de quem esqueceu a senha (E29 bloco 3): gera uma senha aleatoria, grava
 * o hash e limpa {@code senha_alterada_em} — o alvo cai no fluxo de primeiro acesso e e obrigado a
 * trocar. A senha em claro sai daqui uma unica vez, no valor de retorno; nunca e persistida em
 * claro nem loga.
 *
 * <p>Restrito ao mesmo recorte de {@link AtualizarUsuarioUseCase}/{@link DesativarUsuarioUseCase}
 * (so ATENDENTE/SUBGESTOR como alvo, garantido por {@code EquipeRepositorioJdbc}): um GESTOR nao
 * reseta a senha de outro GESTOR ou do ADMINISTRADOR por esta rota, mesma logica que a CRUD de
 * equipe ja aplica.
 *
 * <p>Revoga as sessoes do ALVO, nao de quem chama — alguem que esqueceu a senha pode ter sido
 * comprometido, e a conta comprometida e a do alvo.
 */
@Service
public class DefinirSenhaProvisoriaUseCase {

    /** 12: acima do minimo tipico de politica, para a senha gerada nunca ficar curta por acidente. */
    private static final int TAMANHO_MINIMO_GERADO = 12;

    private final EquipeRepositorio equipe;
    private final CodificadorDeSenha senhas;
    private final PoliticaDeSenha politica;
    private final RefreshTokenRepositorio refreshTokens;

    public DefinirSenhaProvisoriaUseCase(
            EquipeRepositorio equipe,
            CodificadorDeSenha senhas,
            PoliticaDeSenha politica,
            RefreshTokenRepositorio refreshTokens) {
        this.equipe = equipe;
        this.senhas = senhas;
        this.politica = politica;
        this.refreshTokens = refreshTokens;
    }

    @PreAuthorize("hasAnyRole('GESTOR','ADMINISTRADOR')")
    @Transactional
    @Auditable(
            acao = "GERAR_SENHA_PROVISORIA",
            entidadeTipo = "USUARIO",
            capturarDados = false)
    public Optional<String> executar(UUID usuarioId) {
        String senha = GeradorDeSenhaAleatoria.gerar(Math.max(politica.tamanhoMinimo(), TAMANHO_MINIMO_GERADO));
        boolean encontrado = equipe.definirSenhaProvisoria(usuarioId, senhas.codificar(senha));
        if (!encontrado) {
            return Optional.empty();
        }
        refreshTokens.revogarTodosDoUsuario(usuarioId);
        return Optional.of(senha);
    }
}
