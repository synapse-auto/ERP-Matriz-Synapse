package com.synapse.crm.app.cancelamentos;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.relatorios.application.cancelamentos.ValidacaoDoVinculoCancelamento;
import com.synapse.crm.relatorios.domain.cancelamentos.RegistroCancelamentoInvalidoException;

@Component
class ValidacaoDoVinculoCancelamentoAdapter implements ValidacaoDoVinculoCancelamento {
    private final LeadRepositorio leads;
    private final AtendimentoRepositorio atendimentos;
    ValidacaoDoVinculoCancelamentoAdapter(LeadRepositorio leads, AtendimentoRepositorio atendimentos) {
        this.leads=leads; this.atendimentos=atendimentos;
    }
    @Override public void validar(UUID leadId, UUID atendimentoId) {
        if (leadId == null && atendimentoId == null) return;
        if (leadId == null || atendimentoId == null || leads.porId(leadId).isEmpty()
                || atendimentos.porId(atendimentoId).filter(a -> a.leadId().equals(leadId)).isEmpty())
            throw new RegistroCancelamentoInvalidoException();
    }
}
