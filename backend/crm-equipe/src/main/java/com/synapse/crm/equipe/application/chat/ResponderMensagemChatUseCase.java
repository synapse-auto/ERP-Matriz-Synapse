package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Responde no mesmo escopo interno, mantendo a referência sanitizada da mensagem citada. */
@Service
public class ResponderMensagemChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public ResponderMensagemChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "RESPONDER_MENSAGEM_CHAT_INTERNO", entidadeTipo = "CHAT_INTERNO_MENSAGEM", capturarDados = false)
    public ChatInternoRepositorio.MensagemResumo executar(UUID mensagemOrigemId, UUID conversaId,
            String conteudo) {
        UUID remetente = usuario.atual().id();
        exigirAcesso(conversaId, mensagemOrigemId, remetente);
        ChatInternoRepositorio.MensagemResumo salva = repositorio.salvarMensagemComReferencia(
                conversaId, remetente, conteudo, "TEXTO", null, null, conversaId, mensagemOrigemId, "RESPOSTA");
        publicar(salva, remetente);
        return salva;
    }

    private void exigirAcesso(UUID conversaId, UUID mensagemOrigemId, UUID remetente) {
        if (!repositorio.participante(conversaId, remetente)
                || repositorio.mensagem(conversaId, mensagemOrigemId).isEmpty()) {
            throw new ChatSemAcessoException();
        }
    }

    private void publicar(ChatInternoRepositorio.MensagemResumo salva, UUID remetente) {
        var destinatarios = repositorio.participantes(salva.conversaId()).stream()
                .filter(id -> !id.equals(remetente)).toList();
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                salva.conversaId(), salva.id(), remetente, destinatarios, salva.conteudo(), salva.enviadoEm()));
    }
}
