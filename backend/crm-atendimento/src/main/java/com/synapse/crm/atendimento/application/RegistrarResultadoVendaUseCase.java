package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.OrigemResultadoVenda;
import com.synapse.crm.atendimento.domain.atendimento.ResultadoVenda;
import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Mantem um resultado atual por atendimento; alteracoes substituem o valor e permanecem auditadas. */
@Service
public class RegistrarResultadoVendaUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final LeadNoCaminhoDeMensagem leads;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public RegistrarResultadoVendaUseCase(
            AtendimentoRepositorio atendimentos,
            LeadNoCaminhoDeMensagem leads,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.leads = leads;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.finalizar')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Atendimento executar(UUID atendimentoId, UUID atorId, ResultadoVenda resultado) {
        var atual = AtendimentoParaAlteracao.carregar(atendimentoId, atendimentos, leads);
        return registrar(atual, atorId, resultado, OrigemResultadoVenda.MANUAL, Instant.now(relogio), false);
    }

    /** Registra a opcao escolhida no dialogo e a finalizacao na mesma transacao. */
    Atendimento registrarNaFinalizacao(Atendimento atual, UUID atorId, ResultadoVenda resultado, Instant agora) {
        return registrar(atual, atorId, resultado, OrigemResultadoVenda.FINALIZACAO, agora, true);
    }

    private Atendimento registrar(
            Atendimento atual, UUID atorId, ResultadoVenda resultado,
            OrigemResultadoVenda origem, Instant agora, boolean atualizarEstadoCompleto) {
        if (atual.resultadoVenda() == resultado) {
            return atual;
        }
        if (atual.resultadoVenda() != null) {
            throw new ResultadoVendaJaRegistradoException(atual.id());
        }
        Atendimento atualizado = atual.comResultadoVenda(resultado, atual.valorVenda(), atorId, agora, origem);
        if (atualizarEstadoCompleto) {
            atendimentos.salvar(atualizado);
        } else {
            atendimentos.atualizarResultadoVenda(atualizado);
        }
        eventos.publishEvent(new EventoDeAtendimento.ResultadoVendaAtualizado(
                atual.leadId(), atual.id(), atorId, atual.resultadoVenda(), resultado,
                atual.valorVenda(), origem, agora));
        EventosCanonicosDeAtendimento.publicar(
                atendimentos, eventos, EventoCanonicoDeAtendimento.Tipo.RESULTADO_VENDA_ATUALIZADO,
                atual.id(), atual.leadId(), agora);
        return atualizado;
    }
}
