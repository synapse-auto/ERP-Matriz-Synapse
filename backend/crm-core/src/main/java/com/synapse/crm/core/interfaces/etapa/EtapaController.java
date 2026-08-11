package com.synapse.crm.core.interfaces.etapa;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.etapa.GestaoDeEtapasUseCases;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.etapa.ResultadoEtapa;

/** Etapas do funil. Leitura para todos; escrita so para gestor (checado no caso de uso). */
@RestController
@RequestMapping("/api/v1/etapas")
@Tag(name = "Etapas", description = "Etapas configuráveis do funil de atendimento.")
@SecurityRequirement(name = "bearerAuth")
class EtapaController {

    private final GestaoDeEtapasUseCases etapas;

    EtapaController(GestaoDeEtapasUseCases etapas) {
        this.etapas = etapas;
    }

    @Operation(
            summary = "Listar etapas",
            description = "Retorna as etapas do funil na ordem configurada.",
            responses = @ApiResponse(responseCode = "200", description = "Etapas do funil."))
    @GetMapping
    List<EtapaResposta> listar() {
        return etapas.listar().stream().map(EtapaResposta::de).toList();
    }

    @Operation(
            summary = "Criar etapa",
            description = "Inclui uma etapa no funil. Somente papéis de gestão podem executar a operação.",
            responses = @ApiResponse(responseCode = "201", description = "Etapa criada."))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EtapaResposta criar(@Valid @RequestBody EtapaRequisicao requisicao) {
        return EtapaResposta.de(
                etapas.criar(
                        requisicao.nome(),
                        requisicao.ordem(),
                        requisicao.corVisual(),
                        requisicao.resultado()));
    }

    @Operation(
            summary = "Atualizar etapa",
            description = "Substitui nome, ordem, cor visual e resultado comercial da etapa.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Etapa atualizada."),
                @ApiResponse(responseCode = "404", description = "Etapa não encontrada.")
            })
    @PutMapping("/{id}")
    EtapaResposta atualizar(
            @Parameter(description = "Identificador da etapa.", required = true) @PathVariable UUID id,
            @Valid @RequestBody EtapaRequisicao requisicao) {
        return etapas.atualizar(
                        id,
                        requisicao.nome(),
                        requisicao.ordem(),
                        requisicao.corVisual(),
                        requisicao.resultado())
                .map(EtapaResposta::de)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etapa nao encontrada"));
    }

    @Operation(
            summary = "Remover etapa",
            description = "Remove uma etapa existente do funil.",
            responses = {
                @ApiResponse(responseCode = "204", description = "Etapa removida."),
                @ApiResponse(responseCode = "404", description = "Etapa não encontrada.")
            })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@Parameter(description = "Identificador da etapa.", required = true) @PathVariable UUID id) {
        if (!etapas.remover(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Etapa nao encontrada");
        }
    }

    record EtapaRequisicao(
            @Schema(description = "Nome exibido no funil.", example = "Em negociação", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @Size(max = 80) String nome,
            @Schema(description = "Posição positiva no funil.", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
                    @Positive short ordem,
            @Schema(description = "Token de cor opcional.", example = "warning", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    @Size(max = 20) String corVisual,
            @Schema(
                            description = "Resultado comercial; ausente cria etapa em andamento e preserva o valor na edição.",
                            example = "GANHO",
                            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    ResultadoEtapa resultado) {}

    record EtapaResposta(
            UUID id, String nome, short ordem, String corVisual, ResultadoEtapa resultado) {
        static EtapaResposta de(EtapaAtendimento etapa) {
            return new EtapaResposta(
                    etapa.id(), etapa.nome(), etapa.ordem(), etapa.corVisual(), etapa.resultado());
        }
    }
}
