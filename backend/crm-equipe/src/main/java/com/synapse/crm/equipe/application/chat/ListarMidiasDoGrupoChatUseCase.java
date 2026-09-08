package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.MidiaResumo;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Lista anexos de uma conversa interna sem expor a conversa a quem não participa. */
@Service
public class ListarMidiasDoGrupoChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;

    public ListarMidiasDoGrupoChatUseCase(
            ChatInternoRepositorio repositorio, UsuarioContext usuario) {
        this.repositorio = repositorio;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<MidiaResumo> executar(UUID conversaId, int pagina, int tamanho) {
        exigirParticipacao(conversaId);
        int limite = Math.min(50, Math.max(1, tamanho));
        int deslocamento = Math.max(0, pagina) * limite;
        return repositorio.listarMidias(conversaId, limite, deslocamento);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public MidiaResumo executar(UUID conversaId, UUID mensagemId) {
        exigirParticipacao(conversaId);
        return repositorio.midia(conversaId, mensagemId)
                .orElseThrow(() -> new MidiaChatInternoNaoEncontradaException(conversaId, mensagemId));
    }

    private void exigirParticipacao(UUID conversaId) {
        if (!repositorio.participante(conversaId, usuario.atual().id())) {
            throw new ChatSemAcessoException();
        }
    }
}
