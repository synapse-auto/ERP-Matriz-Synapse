package com.synapse.crm.core.application.timeline;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;

/** Consulta a timeline somente depois de provar, pela Specification, que o lead e alcancavel. */
@Service
public class ListarTimelineDoLeadUseCase {

    private final LeadRepositorio leads;
    private final TimelineRepositorio timeline;

    public ListarTimelineDoLeadUseCase(LeadRepositorio leads, TimelineRepositorio timeline) {
        this.leads = leads;
        this.timeline = timeline;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Optional<PaginaTimeline> executar(UUID leadId, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1) {
            throw new IllegalArgumentException("pagina e tamanho da timeline devem ser positivos");
        }
        return leads.porId(leadId).map(lead -> timeline.listar(lead.id(), pagina, tamanho));
    }
}
