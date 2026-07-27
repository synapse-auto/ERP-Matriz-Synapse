package com.synapse.crm.core.application.lead;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.core.domain.lead.Lead;

/**
 * Busca um lead por id.
 *
 * <p>Devolve vazio tanto para "nao existe" quanto para "existe mas nao e seu", e o controller
 * transforma os dois em 404. Responder 403 no segundo caso confirmaria ao atendente que o lead
 * existe e esta com um colega — informacao que a RN-CRM-01 protege.
 */
@Service
public class ObterLeadUseCase {

    private final LeadRepositorio leads;

    public ObterLeadUseCase(LeadRepositorio leads) {
        this.leads = leads;
    }

    @PreAuthorize("isAuthenticated()")
    public Optional<Lead> executar(UUID id) {
        return leads.porId(id);
    }
}
