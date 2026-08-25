package com.synapse.crm.core.application.tag;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.tag.Tag;
import com.synapse.crm.sharedkernel.auditoria.Auditable;

/** Catalogo e aplicacao idempotente de tags pela Automacao. Nunca cria tags novas. */
@Service
public class GerenciarTagsDaAutomacaoUseCase {

    private final LeadRepositorio leads;
    private final TagRepositorio tags;
    private final LeadTagRepositorio vinculos;

    public GerenciarTagsDaAutomacaoUseCase(
            LeadRepositorio leads, TagRepositorio tags, LeadTagRepositorio vinculos) {
        this.leads = leads;
        this.tags = tags;
        this.vinculos = vinculos;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(readOnly = true)
    public List<Tag> listar() {
        return tags.listarTodas();
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    @Auditable(
            acao = "APLICAR_TAG_PELA_AUTOMACAO",
            entidadeTipo = "LEAD",
            capturarDados = false,
            atorTipo = "AUTOMACAO")
    public Tag aplicar(UUID leadId, UUID tagId) {
        var lead = leads.porId(leadId).orElseThrow(() -> new LeadDaAutomacaoNaoEncontradoException(leadId));
        Tag tag = tags.porId(tagId).orElseThrow(() -> new TagDoCatalogoNaoEncontradaException(tagId));
        vinculos.vincular(lead.id(), tag.id());
        return tag;
    }
}
