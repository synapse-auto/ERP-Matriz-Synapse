package com.synapse.crm.atendimento.application.internal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.core.application.lembrete.LembreteDaAutomacaoRepositorio;
import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Cria o lembrete pessoal do responsavel atual, nunca de uma identidade tecnica. */
@Service
public class CriarLembreteDaAutomacaoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final LembreteDaAutomacaoRepositorio lembretes;

    public CriarLembreteDaAutomacaoUseCase(
            AtendimentoRepositorio atendimentos, LembreteDaAutomacaoRepositorio lembretes) {
        this.atendimentos = atendimentos;
        this.lembretes = lembretes;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Lembrete executar(UUID atendimentoId, String texto, Instant dataHora) {
        var atendimento = atendimentos
                .porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("Atendimento", atendimentoId));
        if (atendimento.atendenteId() == null) {
            throw new AtendimentoSemResponsavelException(atendimentoId);
        }
        return lembretes.criar(
                atendimento.leadId(), atendimento.atendenteId(), texto.trim(), dataHora);
    }
}
