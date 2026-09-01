package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Lista participantes do grupo (ou do par direto) para quem ja esta dentro. */
@Service
public class ListarParticipantesChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;

    public ListarParticipantesChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario) {
        this.repositorio = repositorio;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ParticipanteResumo> executar(UUID conversaId) {
        UUID atual = usuario.atual().id();
        if (!repositorio.participante(conversaId, atual)) {
            throw new ChatSemAcessoException();
        }
        return repositorio.participantes(conversaId).stream()
                .map(id -> new ParticipanteResumo(
                        id, repositorio.nomeDoUsuario(id).orElse(id.toString())))
                .toList();
    }

    public record ParticipanteResumo(UUID id, String nome) {}
}
