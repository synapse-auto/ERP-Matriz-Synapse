package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.avaliacao.AtendimentoAindaAbertoParaAvaliacaoException;
import com.synapse.crm.atendimento.domain.avaliacao.AtendimentoSemAtendenteParaAvaliacaoException;
import com.synapse.crm.atendimento.domain.avaliacao.Avaliacao;
import com.synapse.crm.atendimento.domain.avaliacao.AvaliacaoJaRegistradaException;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Grava a nota 0–10 do atendimento no atendente dono.
 *
 * <p>Nao entra no caminho de mensagem: a coleta acontece depois de finalizar, no CRM ou pela
 * Automacao. A visibilidade segue o {@code porId} com RLS — atendente nao avalia conversa de colega.
 */
@Service
public class RegistrarAvaliacaoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final AvaliacaoRepositorio avaliacoes;
    private final Clock relogio;

    public RegistrarAvaliacaoUseCase(
            AtendimentoRepositorio atendimentos, AvaliacaoRepositorio avaliacoes, Clock relogio) {
        this.atendimentos = atendimentos;
        this.avaliacoes = avaliacoes;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Avaliacao executar(UUID atendimentoId, int nota, String comentario) {
        return gravar(atendimentoId, nota, comentario);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Avaliacao executarPelaAutomacao(UUID atendimentoId, int nota, String comentario) {
        return gravar(atendimentoId, nota, comentario);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            readOnly = true)
    public Optional<Avaliacao> consultar(UUID atendimentoId) {
        exigirAtendimentoVisivel(atendimentoId);
        return avaliacoes.porAtendimento(atendimentoId);
    }

    private Avaliacao gravar(UUID atendimentoId, int nota, String comentario) {
        Atendimento atendimento = exigirAtendimentoVisivel(atendimentoId);
        if (atendimento.estaAberto()) {
            throw new AtendimentoAindaAbertoParaAvaliacaoException(atendimentoId);
        }
        if (atendimento.atendenteId() == null) {
            throw new AtendimentoSemAtendenteParaAvaliacaoException(atendimentoId);
        }
        if (avaliacoes.porAtendimento(atendimentoId).isPresent()) {
            throw new AvaliacaoJaRegistradaException(atendimentoId);
        }
        Instant agora = Instant.now(relogio);
        return avaliacoes.salvar(Avaliacao.registrar(
                UUID.randomUUID(),
                atendimentoId,
                atendimento.atendenteId(),
                nota,
                comentario,
                agora));
    }

    private Atendimento exigirAtendimentoVisivel(UUID atendimentoId) {
        return atendimentos
                .porId(atendimentoId)
                .orElseThrow(
                        () -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
    }
}
