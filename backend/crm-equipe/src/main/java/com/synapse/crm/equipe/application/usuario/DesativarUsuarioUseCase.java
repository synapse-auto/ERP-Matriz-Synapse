package com.synapse.crm.equipe.application.usuario;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.autenticacao.RefreshTokenRepositorio;
import com.synapse.crm.equipe.application.permissao.PermissaoRepositorio;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;

/**
 * Desativa um integrante sem apagar historico.
 *
 * <p>Gestao (docs/47): alem de impedir novo login, corta a sessao em curso — refresh tokens
 * revogados, JWT antigo recebe 401 assim que o cache ve a revisao nova, e o tempo real descarta as
 * assinaturas. Antes, o access token continuava valendo ate expirar. GESTOR e ADMINISTRADOR nao sao
 * alvo desta rota; o ultimo responsavel administrativo ativo e protegido tambem no banco (V83).
 */
@Service
public class DesativarUsuarioUseCase {

    private final EquipeRepositorio equipe;
    private final AlcadaSobreIntegrantes alcada;
    private final RefreshTokenRepositorio refreshTokens;
    private final PermissaoRepositorio permissoes;
    private final ApplicationEventPublisher eventos;

    public DesativarUsuarioUseCase(EquipeRepositorio equipe, AlcadaSobreIntegrantes alcada,
            RefreshTokenRepositorio refreshTokens, PermissaoRepositorio permissoes, ApplicationEventPublisher eventos) {
        this.equipe = equipe;
        this.alcada = alcada;
        this.refreshTokens = refreshTokens;
        this.permissoes = permissoes;
        this.eventos = eventos;
    }

    @PreAuthorize("hasAnyRole('SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('equipe.desativar')")
    @Transactional
    @Auditable(acao = "DESATIVAR_USUARIO", entidadeTipo = "USUARIO")
    public boolean executar(UUID id) {
        Optional<Usuario> alvo = equipe.travarParaAlteracao(id);
        if (alvo.isEmpty()) {
            return false;
        }
        alcada.exigirAlvo(id, alvo.get().papel());
        if (!equipe.desativar(id)) {
            return false;
        }
        refreshTokens.revogarTodosDoUsuario(id);
        long revisao = permissoes.incrementarRevisaoGlobal();
        eventos.publishEvent(new AcessoDeUsuariosAlterado(Set.of(id), true, revisao));
        return true;
    }
}
