package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Qualquer participante renomeia o grupo — sem papel de administrador. */
@Service
public class RenomearGrupoChatUseCase {
    private final ChatInternoRepositorio repositorio;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public RenomearGrupoChatUseCase(
            ChatInternoRepositorio repositorio,
            UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void executar(UUID conversaId, String novoNome) {
        UUID ator = usuario.atual().id();
        if (!repositorio.participante(conversaId, ator)) {
            throw new ChatSemAcessoException();
        }
        TipoConversaChat tipo = repositorio.tipoDaConversa(conversaId)
                .orElseThrow(ChatSemAcessoException::new);
        if (tipo != TipoConversaChat.GRUPO) {
            throw new OperacaoDeGrupoInvalidaException("so grupos tem nome");
        }
        String nomeLimpo = novoNome == null ? "" : novoNome.trim();
        if (nomeLimpo.isEmpty()) {
            throw new OperacaoDeGrupoInvalidaException("nome do grupo e obrigatorio");
        }
        String anterior = repositorio.nomeDoGrupo(conversaId).orElse("");
        if (anterior.equals(nomeLimpo)) {
            return;
        }
        repositorio.renomearGrupo(conversaId, nomeLimpo);
        ChatInternoRepositorio.MensagemResumo sistema = repositorio.salvarMensagemSistema(
                conversaId, ator, ConteudoDeSistemaChat.nomeAlterado(anterior, nomeLimpo));
        List<UUID> destinatarios = repositorio.participantes(conversaId).stream()
                .filter(id -> !id.equals(ator))
                .toList();
        eventos.publishEvent(new EventoDeChatInterno.MensagemEnviada(
                conversaId, sistema.id(), ator, destinatarios, sistema.conteudo(), sistema.enviadoEm()));
    }
}
