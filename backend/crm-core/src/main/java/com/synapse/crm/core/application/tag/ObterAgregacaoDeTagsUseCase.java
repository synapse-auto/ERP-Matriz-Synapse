package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.tag.ContagemDeTag;

/**
 * O mini-dashboard de Tags (E17b §Bloco 6).
 *
 * <p>O recorte de visibilidade vem de {@link LeadRepositorio#idsVisiveis()} — a mesma
 * Specification (RN-CRM-01) usada por toda consulta de lead — e so depois entra em {@link
 * LeadTagRepositorio}, que nunca decide visibilidade sozinho. Um atendente que enxerga 6 leads nunca
 * ve "47 leads com a tag Obra": o numerador e o denominador vem do mesmo recorte.
 */
@Service
public class ObterAgregacaoDeTagsUseCase {

    private final LeadRepositorio leads;
    private final LeadTagRepositorio vinculos;

    public ObterAgregacaoDeTagsUseCase(LeadRepositorio leads, LeadTagRepositorio vinculos) {
        this.leads = leads;
        this.vinculos = vinculos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public AgregacaoDeTags executar() {
        List<UUID> visiveis = leads.idsVisiveis();
        long leadsComTag = vinculos.contarLeadsComTag(visiveis);
        List<ContagemDeTag> porTag = vinculos.contarPorTag(visiveis);
        return new AgregacaoDeTags(visiveis.size(), leadsComTag, porTag);
    }
}
