package com.synapse.crm.app.cancelamentos;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.relatorios.application.cancelamentos.ValidacaoDoVinculoCancelamento;
import com.synapse.crm.relatorios.domain.cancelamentos.RegistroCancelamentoInvalidoException;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Component
class ValidacaoDoVinculoCancelamentoAdapter implements ValidacaoDoVinculoCancelamento {
    private final LeadRepositorio leads;
    private final JdbcTemplate geral;
    ValidacaoDoVinculoCancelamentoAdapter(LeadRepositorio leads,
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource dataSource) {
        this.leads=leads; this.geral=new JdbcTemplate(dataSource);
    }
    @Override public void validar(UUID leadId, UUID atendimentoId) {
        if (leadId == null && atendimentoId == null) return;
        if (leadId == null || atendimentoId == null || leads.porId(leadId).isEmpty()
                || !Boolean.TRUE.equals(geral.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM atendimento WHERE id = ? AND lead_id = ?)",
                        Boolean.class, atendimentoId, leadId)))
            throw new RegistroCancelamentoInvalidoException();
    }
}
