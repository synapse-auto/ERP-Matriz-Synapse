package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Remove outro participante ou sai. Grupo plano: qualquer membro pode remover qualquer outro,
 * inclusive quem criou. Ultimo a sair apaga a conversa (sem linha orfa).
 */
@Service
public class RemoverParticipanteGrupoChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public RemoverParticipanteGrupoChatUseCase(
            ChatInternoRepositorio repositorio,
            UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void executar(UUID conversaId, UUID alvoId) {
        UUID ator = usuario.atual().id();
        if (!repositorio.participante(conversaId, ator)) {
            throw new ChatSemAcessoException();
        }
        TipoConversaChat tipo = repositorio.tipoDaConversa(conversaId)
                .orElseThrow(ChatSemAcessoException::new);
        if (tipo != TipoConversaChat.GRUPO) {
            throw new OperacaoDeGrupoInvalidaException("so grupos permitem alterar participantes");
        }
        if (alvoId == null || !repositorio.participante(conversaId, alvoId)) {
            throw new OperacaoDeGrupoInvalidaException("participante nao esta no grupo");
        }

        String nomeAlvo = repositorio.nomeDoUsuario(alvoId).orElse(alvoId.toString());
        boolean saindo = ator.equals(alvoId);
        String conteudo = saindo
                ? ConteudoDeSistemaChat.participanteSaiu(alvoId, nomeAlvo)
                : ConteudoDeSistemaChat.participanteRemovido(alvoId, nomeAlvo);

        ChatInternoRepositorio.MensagemResumo sistema =
                repositorio.salvarMensagemSistema(conversaId, ator, conteudo);
        repositorio.removerParticipante(conversaId, alvoId);

        List<UUID> destinatarios = repositorio.participantes(conversaId).stream()
                .filter(id -> !id.equals(ator))
                .toList();
        if (!destinatarios.isEmpty()) {
            eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                    conversaId,
                    sistema.id(),
                    ator,
                    destinatarios,
                    sistema.conteudo(),
                    sistema.enviadoEm()));
        }
        repositorio.apagarSeSemParticipantes(conversaId);
    }
}
