package com.synapse.crm.core.application.lead;

import java.util.List;

import com.synapse.crm.core.domain.tag.Tag;

/** Valores existentes no recorte visivel da agenda, independentes da pagina carregada. */
public record CatalogosDeFiltroDeLead(List<String> cidades, List<Tag> tags) {

    public CatalogosDeFiltroDeLead {
        cidades = List.copyOf(cidades);
        tags = List.copyOf(tags);
    }
}
