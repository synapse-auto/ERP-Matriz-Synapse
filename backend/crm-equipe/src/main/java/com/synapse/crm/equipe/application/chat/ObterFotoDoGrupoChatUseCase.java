package com.synapse.crm.equipe.application.chat;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Resolve a referencia da foto para quem participa do grupo. O download em si fica fora da
 * transacao (como o das midias do chat): a conexao de banco nao espera o storage.
 */
@Service
public class ObterFotoDoGrupoChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;

    public ObterFotoDoGrupoChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario) {
        this.repositorio = repositorio;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Optional<String> referencia(UUID conversaId) {
        if (!repositorio.participante(conversaId, usuario.atual().id())) {
            throw new ChatSemAcessoException();
        }
        return repositorio.referenciaDaFotoDoGrupo(conversaId);
    }
}
