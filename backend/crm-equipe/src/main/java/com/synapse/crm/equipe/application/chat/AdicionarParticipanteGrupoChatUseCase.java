package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Qualquer participante de GRUPO pode adicionar outro. DIRETA e recusada. */
@Service
public class AdicionarParticipanteGrupoChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public AdicionarParticipanteGrupoChatUseCase(
            ChatInternoRepositorio repositorio,
            UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void executar(UUID conversaId, UUID novoParticipanteId) {
        UUID ator = usuario.atual().id();
        if (!repositorio.participante(conversaId, ator)) {
            throw new ChatSemAcessoException();
        }
        TipoConversaChat tipo = repositorio.tipoDaConversa(conversaId)
                .orElseThrow(ChatSemAcessoException::new);
        if (tipo != TipoConversaChat.GRUPO) {
            throw new OperacaoDeGrupoInvalidaException("conversa direta nao vira grupo");
        }
        if (novoParticipanteId == null || !repositorio.usuarioExiste(novoParticipanteId)) {
            throw new OperacaoDeGrupoInvalidaException("participante invalido ou inativo");
        }
        if (repositorio.participante(conversaId, novoParticipanteId)) {
            return;
        }

        String nomeAlvo = repositorio.nomeDoUsuario(novoParticipanteId).orElse(novoParticipanteId.toString());
        repositorio.adicionarParticipante(conversaId, novoParticipanteId);
        ChatInternoRepositorio.MensagemResumo sistema = repositorio.salvarMensagemSistema(
                conversaId,
                ator,
                ConteudoDeSistemaChat.participanteAdicionado(novoParticipanteId, nomeAlvo));
        List<UUID> destinatarios = repositorio.participantes(conversaId).stream()
                .filter(id -> !id.equals(ator))
                .toList();
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaId, sistema.id(), ator, destinatarios, sistema.conteudo(), sistema.enviadoEm()));
    }
}
