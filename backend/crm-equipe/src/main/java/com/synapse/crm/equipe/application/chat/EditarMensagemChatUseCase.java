package com.synapse.crm.equipe.application.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.chat.MensagemChat;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Edita somente texto próprio; o mesmo ID preserva referências e reações. */
@Service
public class EditarMensagemChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public EditarMensagemChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos, Clock relogio) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "EDITAR_MENSAGEM_CHAT_INTERNO", entidadeTipo = "CHAT_INTERNO_MENSAGEM", capturarDados = false)
    public ChatInternoRepositorio.MensagemResumo executar(UUID mensagemId, UUID conversaId, String conteudo) {
        UUID remetente = usuario.atual().id();
        if (!repositorio.participante(conversaId, remetente)) {
            throw new ChatSemAcessoException();
        }
        ChatInternoRepositorio.MensagemResumo atual = repositorio.mensagem(conversaId, mensagemId)
                .orElseThrow(ChatSemAcessoException::new);
        if (!remetente.equals(atual.remetenteId())) {
            throw new ChatSemAcessoException();
        }
        if (atual.removida() || !"TEXTO".equals(atual.tipo())) {
            throw new OperacaoDeGrupoInvalidaException(
                    "Somente mensagens de texto proprias e nao removidas podem ser editadas.");
        }
        String normalizado = new MensagemChat(conteudo).conteudo();
        Instant editadoEm = Instant.now(relogio);
        ChatInternoRepositorio.MensagemResumo editada = repositorio.editarMensagem(
                conversaId, mensagemId, remetente, normalizado, editadoEm);
        var destinatarios = repositorio.participantes(conversaId).stream()
                .filter(id -> !id.equals(remetente)).toList();
        eventos.publishEvent(new EventoDeChatInterno.MensagemEditada(
                conversaId, editada.id(), remetente, destinatarios, editada.conteudo(),
                editada.enviadoEm(), editadoEm, editada.remetenteNome(), editada.tipo(), editada.midiaMetadados()));
        return editada;
    }
}
