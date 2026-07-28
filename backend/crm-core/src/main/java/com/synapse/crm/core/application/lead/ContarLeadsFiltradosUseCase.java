package com.synapse.crm.core.application.lead;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.core.domain.filtro.FiltroDeLeads;

/**
 * Quantos leads o usuario veria sob o filtro que ele esta montando (E03b).
 *
 * <p>Serve a contagem em tempo real da tela, chamada a cada mudanca de criterio. Por isso e um caso
 * de uso proprio, e nao um {@code executar(...).size()}: o numero sai de um {@code COUNT}, sem
 * carregar linha nenhuma.
 *
 * <p>O <em>debounce</em> e do frontend. Nada aqui protege contra uma tela que dispare uma chamada por
 * tecla digitada, e essa e a combinacao acordada no contrato: agrupamento no cliente, contagem barata
 * no servidor.
 */
@Service
public class ContarLeadsFiltradosUseCase {

    private final LeadRepositorio leads;

    public ContarLeadsFiltradosUseCase(LeadRepositorio leads) {
        this.leads = leads;
    }

    @PreAuthorize("isAuthenticated()")
    public long executar(FiltroDeLeads filtro) {
        return leads.contar(filtro);
    }
}
