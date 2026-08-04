package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.UUID;

import com.synapse.crm.core.domain.tag.Tag;

/** Porta da associacao entre um lead ja autorizado e as tags globais da operacao. */
public interface LeadTagRepositorio {

    List<Tag> listar(UUID leadId);

    void vincular(UUID leadId, UUID tagId);

    void desvincular(UUID leadId, UUID tagId);
}
