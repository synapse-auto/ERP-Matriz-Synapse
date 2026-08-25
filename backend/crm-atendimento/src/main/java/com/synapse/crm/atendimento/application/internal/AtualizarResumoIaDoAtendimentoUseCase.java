package com.synapse.crm.atendimento.application.internal;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.core.application.lead.ResumoIaDoLeadRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Sobrescreve o resumo do lead associado ao atendimento informado. */
@Service
public class AtualizarResumoIaDoAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final ResumoIaDoLeadRepositorio resumos;
    private final int tamanhoMaximo;

    public AtualizarResumoIaDoAtendimentoUseCase(
            AtendimentoRepositorio atendimentos,
            ResumoIaDoLeadRepositorio resumos,
            @Value("${synapse.automacao.resumo-ia-tamanho-maximo}") int tamanhoMaximo) {
        this.atendimentos = atendimentos;
        this.resumos = resumos;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID atendimentoId, String resumo) {
        String normalizado = resumo.trim();
        if (normalizado.length() > tamanhoMaximo) {
            throw new ResumoIaMuitoLongoException(tamanhoMaximo);
        }
        var atendimento = atendimentos
                .porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("Atendimento", atendimentoId));
        resumos.sobrescrever(atendimento.leadId(), normalizado);
        return new Resultado(atendimento.id(), atendimento.leadId(), normalizado);
    }

    public int tamanhoMaximo() {
        return tamanhoMaximo;
    }

    public record Resultado(UUID atendimentoId, UUID leadId, String resumo) {}
}
