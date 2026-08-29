package com.synapse.crm.atendimento.application.reacao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.domain.evento.ReacaoDaMensagemParaTempoReal;
import com.synapse.crm.sharedkernel.emoji.EmojiUnicode;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Service
public class DefinirReacaoDaMensagemUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final ReacaoDeMensagemRepositorio reacoes;
    private final UsuarioContext usuarios;
    private final ApplicationEventPublisher eventos;

    public DefinirReacaoDaMensagemUseCase(
            AtendimentoRepositorio atendimentos,
            ReacaoDeMensagemRepositorio reacoes,
            UsuarioContext usuarios,
            ApplicationEventPublisher eventos) {
        this.atendimentos = atendimentos;
        this.reacoes = reacoes;
        this.usuarios = usuarios;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public List<ResumoDeReacao> executar(UUID atendimentoId, UUID mensagemId, Instant enviadoEm, String emoji) {
        String validado = EmojiUnicode.validar(emoji);
        atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        UUID usuarioId = usuarios.atual().id();
        var chave = new ReacaoDeMensagemRepositorio.Chave(mensagemId, enviadoEm);
        if (!reacoes.definir(chave, atendimentoId, usuarioId, validado)) {
            throw new RecursoDeAtendimentoIndisponivelException("mensagem", mensagemId);
        }
        List<ResumoDeReacao> resumo = reacoes.resumirUma(chave, usuarioId);
        eventos.publishEvent(new ReacaoDaMensagemParaTempoReal(
                atendimentoId, mensagemId, enviadoEm, usuarioId, validado, resumo));
        return resumo;
    }
}
