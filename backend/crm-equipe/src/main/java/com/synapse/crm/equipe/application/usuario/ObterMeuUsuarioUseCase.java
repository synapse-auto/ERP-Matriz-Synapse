package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * {@code GET /api/v1/me} (E17): nome, papel e presenca de quem esta autenticado — leitura dos
 * proprios dados, sem checagem extra de papel.
 *
 * <p>O JWT so carrega {@code papel} (E07 §1); ate esta etapa nao havia caminho para o rodape da
 * Sidebar mostrar o nome real de um ATENDENTE (que nao alcanca {@code GET /api/v1/usuarios},
 * restrito a GESTOR/SUBGESTOR/ADMINISTRADOR).
 */
@Service
public class ObterMeuUsuarioUseCase {

    private final EquipeRepositorio equipe;
    private final UsuarioContext usuario;

    public ObterMeuUsuarioUseCase(EquipeRepositorio equipe, UsuarioContext usuario) {
        this.equipe = equipe;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Optional<Usuario> executar() {
        return equipe.porId(usuario.atual().id());
    }
}
