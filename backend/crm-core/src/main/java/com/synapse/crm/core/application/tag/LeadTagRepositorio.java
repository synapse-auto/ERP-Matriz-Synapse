package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.synapse.crm.core.domain.tag.ContagemDeTag;
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

    /**
     * Quantos, dentre os leads informados, tem pelo menos uma tag (E17b §Bloco 6 — % de leads
     * tagueados do mini-dashboard).
     *
     * <p>{@code leadIds} chega ja recortado pela visibilidade de quem pediu — este metodo, como
     * {@link #listarPorLeads(List)}, nunca consulta {@code lead} para decidir isso sozinho.
     */
    long contarLeadsComTag(List<UUID> leadIds);

    /**
     * Contagem por tag entre os leads informados, da mais usada para a menos usada (E17b §Bloco 6).
     *
     * <p>Mesma premissa de {@link #contarLeadsComTag(List)}: {@code leadIds} e o recorte de
     * visibilidade ja resolvido, nao um filtro que este metodo precise validar.
     */
    List<ContagemDeTag> contarPorTag(List<UUID> leadIds);
}
