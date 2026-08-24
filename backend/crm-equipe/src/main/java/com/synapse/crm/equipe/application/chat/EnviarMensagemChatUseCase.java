package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.chat.MensagemChat;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class EnviarMensagemChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public EnviarMensagemChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ChatInternoRepositorio.MensagemResumo executar(UUID conversaId, String conteudo) {
        UUID remetente = usuario.atual().id();
        if (!repositorio.participante(conversaId, remetente)) throw new ChatSemAcessoException();
        MensagemChat mensagem = new MensagemChat(conteudo);
        var destinatarios = repositorio.participantes(conversaId).stream()
                .filter(id -> !id.equals(remetente)).toList();
        ChatInternoRepositorio.MensagemResumo salva = repositorio.salvarMensagem(conversaId, remetente, mensagem.conteudo());
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaId, salva.id(), remetente, destinatarios, salva.conteudo(), salva.enviadoEm()));
        return salva;
    }
}
