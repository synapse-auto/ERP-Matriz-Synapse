package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.tag.Tag;

/** Remove idempotentemente uma tag de um lead visivel. */
@Service
public class DesvincularTagDoLeadUseCase {

    private final LeadRepositorio leads;
    private final LeadTagRepositorio vinculos;

    public DesvincularTagDoLeadUseCase(LeadRepositorio leads, LeadTagRepositorio vinculos) {
        this.leads = leads;
        this.vinculos = vinculos;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public Optional<List<Tag>> executar(UUID leadId, UUID tagId) {
        return leads.porId(leadId).map(lead -> {
            vinculos.desvincular(lead.id(), tagId);
            return vinculos.listar(lead.id());
        });
    }
}
