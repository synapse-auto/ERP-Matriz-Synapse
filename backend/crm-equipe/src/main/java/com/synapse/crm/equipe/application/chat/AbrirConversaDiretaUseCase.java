package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class AbrirConversaDiretaUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;

    public AbrirConversaDiretaUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario) {
        this.repositorio = repositorio;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public UUID executar(UUID outroUsuario) {
        UUID atual = usuario.atual().id();
        if (outroUsuario == null || atual.equals(outroUsuario) || !repositorio.usuarioExiste(outroUsuario)) {
            throw new IllegalArgumentException("Usuario de destino invalido.");
        }
        return repositorio.conversaDireta(atual, outroUsuario)
                .orElseGet(() -> repositorio.criarConversaDireta(atual, outroUsuario));
    }
}
