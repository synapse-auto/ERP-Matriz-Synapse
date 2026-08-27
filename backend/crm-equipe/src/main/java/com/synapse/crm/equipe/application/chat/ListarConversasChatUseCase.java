package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ListarConversasChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;

    public ListarConversasChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario) {
        this.repositorio = repositorio;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ChatInternoRepositorio.ConversaResumo> executar() {
        return repositorio.listarConversas(usuario.atual().id());
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ChatInternoRepositorio.ConversaResumo> executarPaginado(int limite,
            Instant depoisDe, UUID depoisDoId) {
        return repositorio.listarConversasPaginado(usuario.atual().id(), depoisDe, depoisDoId, limite);
    }
}
