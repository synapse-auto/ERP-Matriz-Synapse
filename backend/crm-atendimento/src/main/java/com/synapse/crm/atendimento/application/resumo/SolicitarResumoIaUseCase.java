package com.synapse.crm.atendimento.application.resumo;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.ResumoIaParaTempoReal;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Cria um ciclo de resumo e o entrega à Automação somente pela outbox. */
@Service
public class SolicitarResumoIaUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final SolicitacaoResumoIaRepositorio solicitacoes;
    private final ResumoIaAutomacaoGateway automacao;
    private final Outbox outbox;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public SolicitarResumoIaUseCase(
            AtendimentoRepositorio atendimentos,
            SolicitacaoResumoIaRepositorio solicitacoes,
            ResumoIaAutomacaoGateway automacao,
            Outbox outbox,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.solicitacoes = solicitacoes;
        this.automacao = automacao;
        this.outbox = outbox;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public SolicitacaoResumoIaRepositorio.Solicitacao executar(UUID atendimentoId, UUID solicitacaoId) {
        if (!automacao.configurado()) throw new ResumoIaAutomacaoDesabilitadoException();
        if (solicitacaoId == null) throw new IdempotencyKeyInvalidaException();
        var atendimento = atendimentos
                .porId(atendimentoId)
                .filter(item -> item.status() == StatusAtendimento.EM_ATENDIMENTO)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        var mesmaSolicitacao = solicitacoes.porId(solicitacaoId);
        if (mesmaSolicitacao.isPresent()) {
            var existente = mesmaSolicitacao.get();
            if (!existente.leadId().equals(atendimento.leadId())
                    || !existente.atendimentoId().equals(atendimentoId)) {
                throw new ResumoIaIdempotenciaIncompativelException(solicitacaoId);
            }
            return existente;
        }
        var atual = solicitacoes.ultimaDoAtendimento(atendimentoId);
        if (atual.isPresent()
                && (atual.get().status() == SolicitacaoResumoIaRepositorio.Status.PENDENTE
                        || atual.get().status() == SolicitacaoResumoIaRepositorio.Status.PROCESSANDO)) {
            return atual.get();
        }
        Instant agora = Instant.now(relogio);
        solicitacoes.criar(solicitacaoId, atendimento.leadId(), atendimentoId, agora);
        outbox.enfileirarSolicitacaoResumoIa(solicitacaoId, atendimento.leadId(), atendimentoId, agora);
        var resposta = solicitacoes.porId(solicitacaoId).orElseThrow();
        eventos.publishEvent(new ResumoIaParaTempoReal(
                atendimentoId, atendimento.leadId(), solicitacaoId, resposta.status().name(), null, agora));
        return resposta;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public SolicitacaoResumoIaRepositorio.Solicitacao estado(UUID atendimentoId) {
        atendimentos.porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        return solicitacoes.ultimaDoAtendimento(atendimentoId).orElse(null);
    }

    public static class ResumoIaAutomacaoDesabilitadoException extends RuntimeException {
        public ResumoIaAutomacaoDesabilitadoException() {
            super("geracao de resumo por IA indisponivel nesta instancia");
        }
    }

    public static class ResumoIaIdempotenciaIncompativelException extends RuntimeException {
        public ResumoIaIdempotenciaIncompativelException(UUID id) {
            super("solicitacao de resumo reutilizada com outro atendimento: " + id);
        }
    }
}
