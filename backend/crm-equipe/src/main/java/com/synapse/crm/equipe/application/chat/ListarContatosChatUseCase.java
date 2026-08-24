package com.synapse.crm.equipe.application.chat;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ListarContatosChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;

    public ListarContatosChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario) {
        this.repositorio = repositorio; this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ChatInternoRepositorio.ContatoResumo> executar() {
        return repositorio.listarContatos(usuario.atual().id());
    }
}
