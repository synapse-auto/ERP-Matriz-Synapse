package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ListarMensagensChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;

    public ListarMensagensChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario) {
        this.repositorio = repositorio;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ChatInternoRepositorio.PaginaMensagens executar(UUID conversaId, Instant antesDe, int limite) {
        UUID atual = usuario.atual().id();
        exigirParticipacao(conversaId, atual);
        return repositorio.listarMensagens(conversaId, atual, antesDe, Math.max(1, Math.min(limite, 100)));
    }

    void exigirParticipacao(UUID conversaId, UUID usuarioId) {
        if (!repositorio.participante(conversaId, usuarioId)) throw new ChatSemAcessoException();
    }
}
