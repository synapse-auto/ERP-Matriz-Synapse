package com.synapse.crm.equipe.application.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class MarcarConversaChatComoLidaUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final Clock relogio;

    public MarcarConversaChatComoLidaUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario, Clock relogio) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void executar(UUID conversaId) {
        UUID atual = usuario.atual().id();
        if (!repositorio.participante(conversaId, atual)) throw new ChatSemAcessoException();
        repositorio.marcarComoLida(conversaId, atual, Instant.now(relogio));
    }
}
