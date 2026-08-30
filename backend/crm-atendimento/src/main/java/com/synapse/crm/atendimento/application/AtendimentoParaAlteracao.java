package com.synapse.crm.atendimento.application;

import java.util.UUID;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;

/** Ordem unica de locks para finalizacao e transferencia, compativel com o envio manual. */
final class AtendimentoParaAlteracao {
    private AtendimentoParaAlteracao() {}

    static Atendimento carregar(
            UUID id, AtendimentoRepositorio atendimentos, LeadNoCaminhoDeMensagem leads) {
        Atendimento visivel = atendimentos.porId(id)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", id));
        if (!leads.bloquearParaAtendimento(visivel.leadId())) {
            throw new RecursoDeAtendimentoIndisponivelException("atendimento", id);
        }
        return atendimentos.porIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", id));
    }
}
