package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.tag.Tag;

/** Lista tags apenas de um lead que a Specification deixou o usuario alcancar. */
@Service
public class ListarTagsDoLeadUseCase {

    private final LeadRepositorio leads;
    private final LeadTagRepositorio vinculos;

    public ListarTagsDoLeadUseCase(LeadRepositorio leads, LeadTagRepositorio vinculos) {
        this.leads = leads;
        this.vinculos = vinculos;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Optional<List<Tag>> executar(UUID leadId) {
        return leads.porId(leadId).map(lead -> vinculos.listar(lead.id()));
    }
}
