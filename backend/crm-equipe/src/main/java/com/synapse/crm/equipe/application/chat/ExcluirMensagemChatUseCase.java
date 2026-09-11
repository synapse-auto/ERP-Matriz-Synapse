package com.synapse.crm.equipe.application.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Exclusão lógica pelo autor; o tombstone remove conteúdo e mídia da leitura. */
@Service
public class ExcluirMensagemChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public ExcluirMensagemChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos, Clock relogio) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "EXCLUIR_MENSAGEM_CHAT_INTERNO", entidadeTipo = "CHAT_INTERNO_MENSAGEM", capturarDados = false)
    public ChatInternoRepositorio.MensagemResumo executar(UUID mensagemId, UUID conversaId) {
        UUID remetente = usuario.atual().id();
        if (!repositorio.participante(conversaId, remetente)
                || repositorio.mensagem(conversaId, mensagemId).isEmpty()) {
            throw new ChatSemAcessoException();
        }
        ChatInternoRepositorio.MensagemResumo removida = repositorio.removerMensagem(
                conversaId, mensagemId, remetente, Instant.now(relogio));
        eventos.publishEvent(new EventoDeChatInterno.MensagemRemovida(
                conversaId, mensagemId, repositorio.participantes(conversaId)));
        return removida;
    }
}
