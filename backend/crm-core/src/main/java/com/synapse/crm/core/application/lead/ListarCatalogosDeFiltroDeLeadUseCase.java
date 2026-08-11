package com.synapse.crm.core.application.lead;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.tag.LeadTagRepositorio;

/** Monta os dropdowns rapidos da agenda sobre todo o recorte visivel, nunca sobre uma pagina. */
@Service
public class ListarCatalogosDeFiltroDeLeadUseCase {

    private final LeadRepositorio leads;
    private final LeadTagRepositorio vinculos;

    public ListarCatalogosDeFiltroDeLeadUseCase(
            LeadRepositorio leads, LeadTagRepositorio vinculos) {
        this.leads = leads;
        this.vinculos = vinculos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public CatalogosDeFiltroDeLead executar() {
        List<UUID> visiveis = leads.idsVisiveis();
        return new CatalogosDeFiltroDeLead(
                leads.localizacoesVisiveis(),
                vinculos.contarPorTag(visiveis).stream().map(contagem -> contagem.tag()).toList());
    }
}
