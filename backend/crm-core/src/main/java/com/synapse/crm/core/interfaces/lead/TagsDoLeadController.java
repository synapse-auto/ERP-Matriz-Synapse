package com.synapse.crm.core.interfaces.lead;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.tag.DesvincularTagDoLeadUseCase;
import com.synapse.crm.core.application.tag.ListarTagsDoLeadUseCase;
import com.synapse.crm.core.application.tag.VincularTagAoLeadUseCase;
import com.synapse.crm.core.domain.tag.Tag;

/** Consulta e edita tags de um lead sem expor uma consulta que contorne a Specification. */
@RestController
@RequestMapping("/api/v1/leads")
class TagsDoLeadController {

    private final ListarTagsDoLeadUseCase listar;
    private final VincularTagAoLeadUseCase vincular;
    private final DesvincularTagDoLeadUseCase desvincular;

    TagsDoLeadController(
            ListarTagsDoLeadUseCase listar,
            VincularTagAoLeadUseCase vincular,
            DesvincularTagDoLeadUseCase desvincular) {
        this.listar = listar;
        this.vincular = vincular;
        this.desvincular = desvincular;
    }

    @GetMapping("/{leadId}/tags")
    List<TagResposta> listar(@PathVariable UUID leadId) {
        return listar.executar(leadId)
                .map(TagsDoLeadController::responder)
                .orElseThrow(TagsDoLeadController::naoEncontrado);
    }

    @PutMapping("/{leadId}/tags/{tagId}")
    List<TagResposta> vincular(@PathVariable UUID leadId, @PathVariable UUID tagId) {
        return vincular.executar(leadId, tagId)
                .map(TagsDoLeadController::responder)
                .orElseThrow(TagsDoLeadController::naoEncontrado);
    }

    @DeleteMapping("/{leadId}/tags/{tagId}")
    List<TagResposta> desvincular(@PathVariable UUID leadId, @PathVariable UUID tagId) {
        return desvincular.executar(leadId, tagId)
                .map(TagsDoLeadController::responder)
                .orElseThrow(TagsDoLeadController::naoEncontrado);
    }

    private static List<TagResposta> responder(List<Tag> tags) {
        return tags.stream().map(TagResposta::de).toList();
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead ou tag nao encontrada");
    }

    record TagResposta(UUID id, String nome, String cor, String icone) {
        static TagResposta de(Tag tag) {
            return new TagResposta(tag.id(), tag.nome(), tag.cor(), tag.icone());
        }
    }
}
