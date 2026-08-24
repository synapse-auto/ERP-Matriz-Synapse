package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Atualiza somente o nome do usuario autenticado; identidade e papel ficam fora deste fluxo. */
@Service
public class AtualizarMeuUsuarioUseCase {

    private final EquipeRepositorio equipe;
    private final UsuarioContext usuario;

    public AtualizarMeuUsuarioUseCase(EquipeRepositorio equipe, UsuarioContext usuario) {
        this.equipe = equipe;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Optional<Usuario> executar(String nome) {
        return equipe.atualizarNomeDoProprio(usuario.atual().id(), nome.trim());
    }
}
