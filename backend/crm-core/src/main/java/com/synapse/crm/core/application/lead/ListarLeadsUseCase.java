package com.synapse.crm.core.application.lead;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.core.domain.lead.LeadResumo;

/**
 * Lista os leads visiveis a quem pediu.
 *
 * <p>Qualquer usuario autenticado pode chamar; o que muda entre papeis nao e a permissao de listar,
 * e o conjunto devolvido. Esse recorte e do {@link LeadRepositorio}, que nao sabe listar sem ele.
 *
 * <p>Devolve {@link LeadResumo}: campos longos nao entram em tela de lista.
 */
@Service
public class ListarLeadsUseCase {

    private final LeadRepositorio leads;

    public ListarLeadsUseCase(LeadRepositorio leads) {
        this.leads = leads;
    }

    @PreAuthorize("isAuthenticated()")
    public List<LeadResumo> executar(FiltroLead filtro) {
        return leads.listar(filtro == null ? FiltroLead.nenhum() : filtro);
    }
}
