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
 */
@Service
public class FinalizarAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public FinalizarAtendimentoUseCase(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento executar(UUID atendimentoId, UUID quemFinalizou) {
        return finalizar(atendimentoId, quemFinalizou);
    }

    /** Entrada exclusiva do caso de uso de lote; nao exposta como parametro HTTP. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(
            transactionManager = Pools.CHAT_TRANSACTION_MANAGER,
            noRollbackFor = {
                AtendimentoJaFinalizadoException.class, RecursoDeAtendimentoIndisponivelException.class
            })
    public Atendimento executarEmLote(UUID atendimentoId, UUID quemFinalizou) {
        return finalizar(atendimentoId, quemFinalizou);
    }

    private Atendimento finalizar(UUID atendimentoId, UUID quemFinalizou) {
        Atendimento aberto = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        Instant agora = Instant.now(relogio);

        Atendimento finalizado = atendimentos.salvar(aberto.finalizar(agora));
        leads.marcarStatus(aberto.leadId(), StatusBasicoLead.FINALIZADO);

        eventos.publishEvent(new EventoDeAtendimento.AtendimentoFinalizado(
                aberto.leadId(), aberto.id(), quemFinalizou, agora));

        return finalizado;
    }
}
