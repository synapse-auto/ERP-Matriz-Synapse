package com.synapse.crm.equipe.application.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class RemoverReacaoChatUseCase {
    private final ChatInternoRepositorio conversas;
    private final ReacaoDeChatInternoRepositorio reacoes;
    private final UsuarioContext usuario;
    private final ApplicationEventPublisher eventos;

    public RemoverReacaoChatUseCase(
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
    public List<ResumoDeReacao> executar(UUID conversaId, UUID mensagemId) {
        UUID atual = usuario.atual().id();
        if (!conversas.participante(conversaId, atual)) {
            throw new ChatSemAcessoException();
        }
        reacoes.remover(conversaId, mensagemId, atual);
        List<ResumoDeReacao> resumo = reacoes.resumirUma(mensagemId, atual);
        eventos.publishEvent(new EventoDeChatInterno.ReacaoAlterada(
                conversaId, mensagemId, conversas.participantes(conversaId), resumo));
        return resumo;
    }
}
