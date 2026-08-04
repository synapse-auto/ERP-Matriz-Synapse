package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.tag.Tag;

/** Vincula uma tag global sem permitir que o endpoint atravesse a visibilidade do lead. */
@Service
public class VincularTagAoLeadUseCase {

    private final LeadRepositorio leads;
    private final TagRepositorio tags;
    private final LeadTagRepositorio vinculos;

    public VincularTagAoLeadUseCase(
            LeadRepositorio leads, TagRepositorio tags, LeadTagRepositorio vinculos) {
        this.leads = leads;
        this.tags = tags;
        this.vinculos = vinculos;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public Optional<List<Tag>> executar(UUID leadId, UUID tagId) {
        return leads.porId(leadId).flatMap(lead -> tags.porId(tagId).map(tag -> {
            vinculos.vincular(lead.id(), tag.id());
            return vinculos.listar(lead.id());
        }));
    }
}
