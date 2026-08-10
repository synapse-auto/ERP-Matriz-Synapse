package com.synapse.crm.core.interfaces.tag;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.tag.AgregacaoDeTags;
import com.synapse.crm.core.application.tag.GestaoDeTagsUseCases;
import com.synapse.crm.core.application.tag.NomeDeTagEmUsoException;
import com.synapse.crm.core.application.tag.ObterAgregacaoDeTagsUseCase;
import com.synapse.crm.core.domain.tag.ContagemDeTag;
import com.synapse.crm.core.domain.tag.Tag;

/** CRUD de tags. A restricao por papel esta nos casos de uso, nao aqui. */
@RestController
@RequestMapping("/api/v1/tags")
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "Tags",
        description = "Catálogo de tags usado para classificar leads.")
@SecurityRequirement(name = "bearerAuth")
class TagController {

    private final GestaoDeTagsUseCases tags;
    private final ObterAgregacaoDeTagsUseCase agregacao;

    TagController(GestaoDeTagsUseCases tags, ObterAgregacaoDeTagsUseCase agregacao) {
        this.tags = tags;
        this.agregacao = agregacao;
    }

    @Operation(
            summary = "Listar tags",
            description = "Retorna o catálogo completo de tags disponível na instância.",
            responses = @ApiResponse(responseCode = "200", description = "Catálogo de tags."))
    @GetMapping
    List<TagResposta> listar() {
        return tags.listar().stream().map(TagResposta::de).toList();
    }

    /**
     * Mini-dashboard de Tags (E17b §Bloco 6). {@code totalLeadsVisiveis} e {@code leadsComTag} vem do
     * mesmo recorte de visibilidade que a listagem de leads — nunca o total da operacao para quem
     * enxerga so a propria carteira.
     */
    @Operation(
            summary = "Agregação de tags",
            description = "Total de leads visíveis, quantos têm ao menos uma tag e a contagem por tag, dentro do mesmo recorte de visibilidade da listagem de leads.",
            responses = @ApiResponse(responseCode = "200", description = "Agregação de tags do recorte visível."))
    @GetMapping("/agregacao")
    AgregacaoDeTagsResposta agregacao() {
        return AgregacaoDeTagsResposta.de(agregacao.executar());
    }

    @Operation(
            summary = "Criar tag",
            description = "Cria uma tag. A permissão de gestão continua sendo validada pelo caso de uso.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Tag criada."),
                @ApiResponse(responseCode = "409", description = "Já existe uma tag com o nome informado.")
            })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TagResposta criar(@Valid @RequestBody TagRequisicao requisicao) {
        return TagResposta.de(
                tags.criar(requisicao.nome(), requisicao.cor(), requisicao.icone()));
    }

    @Operation(
            summary = "Atualizar tag",
            description = "Substitui nome, cor e ícone de uma tag existente.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Tag atualizada."),
                @ApiResponse(responseCode = "404", description = "Tag não encontrada."),
                @ApiResponse(responseCode = "409", description = "Nome já usado por outra tag.")
            })
    @PutMapping("/{id}")
    TagResposta atualizar(
            @Parameter(description = "Identificador da tag.", required = true) @PathVariable UUID id,
            @Valid @RequestBody TagRequisicao requisicao) {
        return tags.atualizar(id, requisicao.nome(), requisicao.cor(), requisicao.icone())
                .map(TagResposta::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag nao encontrada"));
    }

    @Operation(
            summary = "Remover tag",
            description = "Remove uma tag do catálogo.",
            responses = {
                @ApiResponse(responseCode = "204", description = "Tag removida."),
                @ApiResponse(responseCode = "404", description = "Tag não encontrada.")
            })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@Parameter(description = "Identificador da tag.", required = true) @PathVariable UUID id) {
        if (!tags.remover(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag nao encontrada");
        }
    }

    @ExceptionHandler(NomeDeTagEmUsoException.class)
    ProblemDetail aoConflitarNome(NomeDeTagEmUsoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Nome de tag em uso");
        return problema;
    }

    record TagRequisicao(
            @Schema(description = "Nome único da tag.", example = "Prioridade", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @Size(max = 60) String nome,
            @Schema(description = "Token ou valor visual aceito pelo tema.", example = "destructive", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @Size(max = 20) String cor,
            @Schema(description = "Nome opcional do ícone.", example = "flag", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 60) String icone) {}

    record TagResposta(
            @Schema(description = "Identificador da tag.") UUID id,
            @Schema(description = "Nome da tag.") String nome,
            @Schema(description = "Token ou valor visual.") String cor,
            @Schema(description = "Nome do ícone, quando configurado.") String icone) {
        static TagResposta de(Tag tag) {
            return new TagResposta(tag.id(), tag.nome(), tag.cor(), tag.icone());
        }
    }

    /**
     * {@code percentualTagueados} e {@code tagMaisUsada} sao calculados aqui, na borda HTTP, a partir
     * de {@code porTag}/{@code leadsComTag} — nunca armazenados, para nao existir um numero que possa
     * discordar do resto da resposta.
     */
    record AgregacaoDeTagsResposta(
            @Schema(description = "Quantos leads o usuário da requisição enxerga, no mesmo recorte da listagem.") long totalLeadsVisiveis,
            @Schema(description = "Quantos, dentre os visíveis, têm ao menos uma tag.") long leadsComTag,
            @Schema(description = "leadsComTag / totalLeadsVisiveis × 100. Zero quando não há lead visível.") double percentualTagueados,
            @Schema(description = "A tag com mais leads associados, ou null se nenhum lead visível tem tag.") ContagemDeTagResposta tagMaisUsada,
            @Schema(description = "Contagem por tag, da mais usada para a menos usada.") List<ContagemDeTagResposta> porTag) {

        static AgregacaoDeTagsResposta de(AgregacaoDeTags agregacao) {
            List<ContagemDeTagResposta> porTag =
                    agregacao.porTag().stream().map(ContagemDeTagResposta::de).toList();
            double percentual = agregacao.totalLeadsVisiveis() == 0
                    ? 0.0
                    : (agregacao.leadsComTag() * 100.0) / agregacao.totalLeadsVisiveis();
            ContagemDeTagResposta maisUsada = porTag.isEmpty() ? null : porTag.get(0);
            return new AgregacaoDeTagsResposta(
                    agregacao.totalLeadsVisiveis(), agregacao.leadsComTag(), percentual, maisUsada, porTag);
        }
    }

    record ContagemDeTagResposta(
            @Schema(description = "Identificador da tag.") UUID id,
            @Schema(description = "Nome da tag.") String nome,
            @Schema(description = "Token ou valor visual.") String cor,
            @Schema(description = "Nome do ícone, quando configurado.") String icone,
            @Schema(description = "Quantos leads visíveis usam esta tag.") long quantidade) {

        static ContagemDeTagResposta de(ContagemDeTag contagem) {
            return new ContagemDeTagResposta(
                    contagem.tag().id(),
                    contagem.tag().nome(),
                    contagem.tag().cor(),
                    contagem.tag().icone(),
                    contagem.quantidade());
        }
    }
}
