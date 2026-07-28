package com.synapse.crm.core.application.lead;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.core.domain.lead.LeadResumo;

/**
 * Aplica o filtro modular e devolve os leads que o usuario enxerga sob ele (E03b).
 *
 * <p>Atendente pode chamar. O que muda entre papeis nao e a permissao, e o conjunto: o filtro do
 * usuario compoe com {@code AND} por cima da regra de visibilidade dentro do repositorio, entao ele
 * so consegue reduzir o proprio recorte.
 */
@Service
public class FiltrarLeadsUseCase {

    private final LeadRepositorio leads;

    public FiltrarLeadsUseCase(LeadRepositorio leads) {
        this.leads = leads;
    }

    @PreAuthorize("isAuthenticated()")
    public List<LeadResumo> executar(FiltroDeLeads filtro) {
        return leads.listar(filtro);
    }
}
