package com.synapse.crm.app.cancelamentos;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.relatorios.application.cancelamentos.IdentificacaoDoLeadCancelamento;
import com.synapse.crm.sharedkernel.permissao.VerificadorDeCapacidades;

@Component
class IdentificacaoDoLeadCancelamentoAdapter implements IdentificacaoDoLeadCancelamento {
    private final LeadRepositorio leads;
    private final VerificadorDeCapacidades capacidades;

    IdentificacaoDoLeadCancelamentoAdapter(LeadRepositorio leads, VerificadorDeCapacidades capacidades) {
        this.leads = leads;
        this.capacidades = capacidades;
    }

    @Override public Optional<DadosDeIdentificacaoDoCancelamento> obter(UUID leadId) {
        if (leadId == null || !capacidades.permite("atendimentos.ver")) return Optional.empty();
        return leads.identificacao(leadId).map(dados -> new DadosDeIdentificacaoDoCancelamento(dados.cpf(), dados.telefone()));
    }
}
