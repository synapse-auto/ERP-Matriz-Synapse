package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.synapse.crm.core.domain.tag.Tag;

/** Porta da associacao entre um lead ja autorizado e as tags globais da operacao. */
public interface LeadTagRepositorio {

    List<Tag> listar(UUID leadId);

    /**
     * Tags de varios leads de uma vez, para telas de lista (E16 §Bloco 1) — uma consulta so, nunca
     * uma por linha. Leads sem tag simplesmente nao aparecem como chave do mapa.
     */
    Map<UUID, List<Tag>> listarPorLeads(List<UUID> leadIds);

    void vincular(UUID leadId, UUID tagId);

    void desvincular(UUID leadId, UUID tagId);
}
