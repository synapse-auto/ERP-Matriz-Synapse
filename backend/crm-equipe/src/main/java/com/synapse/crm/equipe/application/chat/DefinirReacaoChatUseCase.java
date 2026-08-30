package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.emoji.EmojiUnicode;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class DefinirReacaoChatUseCase {
    private final ChatInternoRepositorio conversas;
    private final ReacaoDeChatInternoRepositorio reacoes;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public DefinirReacaoChatUseCase(
            ChatInternoRepositorio conversas,
            ReacaoDeChatInternoRepositorio reacoes,
            UsuarioContext usuario,
            ApplicationEventPublisher eventos) {
        this.conversas = conversas;
        this.reacoes = reacoes;
        this.usuario = usuario;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public List<ResumoDeReacao> executar(UUID conversaId, UUID mensagemId, String emoji) {
        String validado = EmojiUnicode.validar(emoji);
        UUID atual = usuario.atual().id();
        if (!conversas.participante(conversaId, atual)) {
            throw new ChatSemAcessoException();
        }
        if (!reacoes.definir(conversaId, mensagemId, atual, validado)) {
            throw new ChatSemAcessoException();
        }
        List<ResumoDeReacao> resumo = reacoes.resumirUma(mensagemId, atual);
        eventos.publishEvent(new EventoDeChatInterno.ReacaoAlterada(
                conversaId, mensagemId, atual, validado, conversas.participantes(conversaId), resumo));
        return resumo;
    }
}
