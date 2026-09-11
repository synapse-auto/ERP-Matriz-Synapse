package com.synapse.crm.equipe.application.chat;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Encaminha uma cópia para uma única conversa interna autorizada pelo participante. */
@Service
public class EncaminharMensagemChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public EncaminharMensagemChatUseCase(ChatInternoRepositorio repositorio, UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    @Auditable(acao = "ENCAMINHAR_MENSAGEM_CHAT_INTERNO", entidadeTipo = "CHAT_INTERNO_MENSAGEM", capturarDados = false)
    public ChatInternoRepositorio.MensagemResumo executar(UUID mensagemOrigemId, UUID conversaOrigemId,
            UUID conversaDestinoId) {
        UUID remetente = usuario.atual().id();
        if (!repositorio.participante(conversaOrigemId, remetente)
                || !repositorio.participante(conversaDestinoId, remetente)) {
            throw new ChatSemAcessoException();
        }
        ChatInternoRepositorio.MensagemResumo origem = repositorio.mensagem(conversaOrigemId, mensagemOrigemId)
                .orElseThrow(ChatSemAcessoException::new);
        if (origem.removida() || "SISTEMA".equals(origem.tipo())) {
            throw new OperacaoDeGrupoInvalidaException("Mensagem nao pode ser encaminhada.");
        }
        ChatInternoRepositorio.MensagemResumo salva = repositorio.salvarMensagemComReferencia(
                conversaDestinoId, remetente, origem.conteudo(), origem.tipo(), origem.midiaUrl(),
                origem.midiaMetadados(), conversaOrigemId, mensagemOrigemId, "ENCAMINHAMENTO");
        var destinatarios = repositorio.participantes(conversaDestinoId).stream()
                .filter(id -> !id.equals(remetente)).toList();
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaDestinoId, salva.id(), remetente, destinatarios, salva.conteudo(), salva.enviadoEm(),
                salva.remetenteNome(), salva.tipo(), salva.midiaMetadados()));
        return salva;
    }
}
