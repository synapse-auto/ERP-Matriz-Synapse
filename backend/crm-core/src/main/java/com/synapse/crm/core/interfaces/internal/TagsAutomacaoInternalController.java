package com.synapse.crm.core.interfaces.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.tag.GerenciarTagsDaAutomacaoUseCase;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.application.tag.TagDoCatalogoNaoEncontradaException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Catalogo fechado de tags e sua aplicacao idempotente pela Automacao. */
@RestController
@RequestMapping("/internal/v1")
@Tag(
        name = "Tags internas",
        description = "Catálogo existente e classificação de leads pela Automação, sem criação implícita.")
@SecurityRequirement(name = "synapseToken")
class TagsAutomacaoInternalController {

    private final GerenciarTagsDaAutomacaoUseCase tags;

    TagsAutomacaoInternalController(GerenciarTagsDaAutomacaoUseCase tags) {
        this.tags = tags;
    }

    @Operation(
            summary = "Listar catálogo de tags",
            description = "Retorna somente as tags previamente configuradas pela gestão da instância.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Catálogo completo, possivelmente vazio."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/tags")
    List<TagResposta> listar() {
        return ContextoDeServico.buscarComo(
                "listar-tags-automacao",
                () -> tags.listar().stream().map(TagResposta::de).toList());
    }

    @Operation(
            summary = "Aplicar tag existente ao lead",
            description = "Vincula uma tag do catálogo ao lead. Reaplicar vínculo existente é sucesso idempotente; este contrato nunca cria tags.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Tag aplicada ou vínculo já existente."),
                @ApiResponse(responseCode = "400", description = "Corpo inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente."),
                @ApiResponse(responseCode = "422", description = "Tag informada não existe no catálogo.")
            })
    @PostMapping("/leads/{id}/tags")
    TagAplicadaResposta aplicar(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID id,
            @Valid @RequestBody AplicacaoRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "aplicar-tag-automacao",
                () -> new TagAplicadaResposta(id, TagResposta.de(tags.aplicar(id, requisicao.tagId()))));
    }

    @ExceptionHandler(LeadDaAutomacaoNaoEncontradoException.class)
    ProblemDetail leadNaoEncontrado(LeadDaAutomacaoNaoEncontradoException erro) {
        return problema(HttpStatus.NOT_FOUND, "Lead nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(TagDoCatalogoNaoEncontradaException.class)
    ProblemDetail tagNaoEncontrada(TagDoCatalogoNaoEncontradaException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Tag fora do catalogo", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record AplicacaoRequisicao(
            @Schema(description = "UUID de uma tag já existente no catálogo.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull UUID tagId) {}

    record TagResposta(UUID id, String nome, String cor, String icone) {
        static TagResposta de(com.synapse.crm.core.domain.tag.Tag tag) {
            return new TagResposta(tag.id(), tag.nome(), tag.cor(), tag.icone());
        }
    }

    record TagAplicadaResposta(UUID leadId, TagResposta tag) {}
}
