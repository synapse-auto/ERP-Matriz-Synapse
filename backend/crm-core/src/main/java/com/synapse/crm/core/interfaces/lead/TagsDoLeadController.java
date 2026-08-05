package com.synapse.crm.core.interfaces.lead;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Tags dos leads",
        description = "Consulta e vínculo de tags sem contornar a visibilidade dos leads.")
@SecurityRequirement(name = "bearerAuth")
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

    @Operation(summary = "Listar tags do lead", description = "Retorna as tags de um lead visível.", responses = {@ApiResponse(responseCode = "200", description = "Tags vinculadas."), @ApiResponse(responseCode = "404", description = "Lead inexistente ou não visível.")})
    @GetMapping("/{leadId}/tags")
    List<TagResposta> listar(@Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID leadId) {
        return listar.executar(leadId)
                .map(TagsDoLeadController::responder)
                .orElseThrow(TagsDoLeadController::naoEncontrado);
    }

    @Operation(summary = "Vincular tag ao lead", description = "Vincula uma tag existente a um lead visível.", responses = {@ApiResponse(responseCode = "200", description = "Lista atualizada de tags."), @ApiResponse(responseCode = "404", description = "Lead ou tag inexistente, ou lead não visível.")})
    @PutMapping("/{leadId}/tags/{tagId}")
    List<TagResposta> vincular(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID leadId,
            @Parameter(description = "Identificador da tag.", required = true) @PathVariable UUID tagId) {
        return vincular.executar(leadId, tagId)
                .map(TagsDoLeadController::responder)
                .orElseThrow(TagsDoLeadController::naoEncontrado);
    }

    @Operation(summary = "Desvincular tag do lead", description = "Remove o vínculo entre uma tag e um lead visível.", responses = {@ApiResponse(responseCode = "200", description = "Lista atualizada de tags."), @ApiResponse(responseCode = "404", description = "Lead ou tag inexistente, ou lead não visível.")})
    @DeleteMapping("/{leadId}/tags/{tagId}")
    List<TagResposta> desvincular(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID leadId,
            @Parameter(description = "Identificador da tag.", required = true) @PathVariable UUID tagId) {
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
