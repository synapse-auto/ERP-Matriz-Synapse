package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Consome a classificacao do n8n para o ciclo de atendimento indicado, sem analisar conversa. */
@Service
public class ClassificarNegociacaoDoAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public ClassificarNegociacaoDoAtendimentoUseCase(
            AtendimentoRepositorio atendimentos,
            com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public ClassificacaoNegociacaoResposta executar(UUID atendimentoId, boolean emNegociacao) {
        var atual = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        if (!atual.estaAberto()) {
            throw new com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException(
                    atendimentoId, "classificacao da automacao");
        }
        if (atual.emNegociacao() == emNegociacao) {
            return new ClassificacaoNegociacaoResposta(atendimentoId, emNegociacao, false);
        }
        Instant agora = Instant.now(relogio);
        atendimentos.salvar(atual.comNegociacao(emNegociacao));
        eventos.publishEvent(new EventoDeAtendimento.ClassificacaoDeNegociacaoAtualizada(
                atual.leadId(), atendimentoId, emNegociacao, agora));
        EventosCanonicosDeAtendimento.publicar(
                atendimentos, eventos, EventoCanonicoDeAtendimento.Tipo.CLASSIFICACAO_NEGOCIACAO_ALTERADA,
                atendimentoId, atual.leadId(), agora);
        return new ClassificacaoNegociacaoResposta(atendimentoId, emNegociacao, true);
    }

    /** Valida antes da reserva FK da Idempotency-Key, sem produzir efeito. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void validar(UUID atendimentoId) {
        var atual = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        if (!atual.estaAberto()) {
            throw new com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException(
                    atendimentoId, "classificacao da automacao");
        }
    }

    public record ClassificacaoNegociacaoResposta(UUID atendimentoId, boolean emNegociacao, boolean alterado) {}
}
