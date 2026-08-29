package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class ListarMensagensChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final ReacaoDeChatInternoRepositorio reacoes;
    private final UsuarioContext usuario;

    public ListarMensagensChatUseCase(
            ChatInternoRepositorio repositorio,
            ReacaoDeChatInternoRepositorio reacoes,
            UsuarioContext usuario) {
        this.repositorio = repositorio;
        this.reacoes = reacoes;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ChatInternoRepositorio.PaginaMensagens executar(UUID conversaId, Instant antesDe, int limite) {
        UUID atual = usuario.atual().id();
        exigirParticipacao(conversaId, atual);
        ChatInternoRepositorio.PaginaMensagens pagina =
                repositorio.listarMensagens(conversaId, atual, antesDe, Math.max(1, Math.min(limite, 100)));
        var resumos = reacoes.resumir(
                pagina.mensagens().stream().map(ChatInternoRepositorio.MensagemResumo::id).toList(), atual);
        List<ChatInternoRepositorio.MensagemResumo> comReacoes = pagina.mensagens().stream()
                .map(mensagem -> mensagem.comReacoes(resumos.getOrDefault(mensagem.id(), List.of())))
                .toList();
        return new ChatInternoRepositorio.PaginaMensagens(comReacoes, pagina.proximoCursor());
    }

    void exigirParticipacao(UUID conversaId, UUID usuarioId) {
        if (!repositorio.participante(conversaId, usuarioId)) throw new ChatSemAcessoException();
    }
}
