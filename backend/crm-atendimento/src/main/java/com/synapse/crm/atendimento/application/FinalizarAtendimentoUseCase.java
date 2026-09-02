package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Encerra o atendimento e leva o lead junto para {@code FINALIZADO}.
 *
 * <p>Finalizar duas vezes falha, em vez de virar no-op: o agregado recusa, e a recusa importa porque
 * um segundo encerramento publicaria um segundo evento e a timeline do lead contaria uma historia que
 * nao aconteceu.
 *
 * <p><b>Avaliacao (contrato EV-08 §1.1 e §1.5):</b> a pesquisa de satisfacao so e enfileirada na
 * finalizacao <em>individual</em>. "Finalizar todos" nunca dispara — tres cliques de lote virariam
 * dezenas de conversas abertas com o cliente. A origem nao vem de parametro HTTP nem de heuristica:
 * quem chama ja sabe qual e, porque lote e individual entram por metodos publicos distintos.
 */
@Service
public class FinalizarAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final SolicitacaoDeAvaliacao avaliacao;

    public FinalizarAtendimentoUseCase(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio,
            SolicitacaoDeAvaliacao avaliacao) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
        this.avaliacao = avaliacao;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento executar(UUID atendimentoId, UUID quemFinalizou) {
        return finalizar(atendimentoId, quemFinalizou, Origem.INDIVIDUAL);
    }

    /** Entrada exclusiva do caso de uso de lote; nao exposta como parametro HTTP. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento executarEmLote(UUID atendimentoId, UUID quemFinalizou) {
        return finalizar(atendimentoId, quemFinalizou, Origem.LOTE);
    }

    private Atendimento finalizar(UUID atendimentoId, UUID quemFinalizou, Origem origem) {
        Atendimento aberto = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        Instant agora = Instant.now(relogio);

        Atendimento finalizado = atendimentos.salvar(aberto.finalizar(agora));
        leads.marcarStatus(aberto.leadId(), StatusBasicoLead.FINALIZADO);
        if (origem == Origem.INDIVIDUAL) {
            avaliacao.preparar(finalizado);
        }

        eventos.publishEvent(new EventoDeAtendimento.AtendimentoFinalizado(
                aberto.leadId(), aberto.id(), quemFinalizou, agora));

        return finalizado;
    }

    private enum Origem { INDIVIDUAL, LOTE }
}
