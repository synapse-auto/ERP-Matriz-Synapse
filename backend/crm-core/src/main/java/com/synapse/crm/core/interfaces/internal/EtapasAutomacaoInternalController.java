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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.etapa.EtapaNaoEncontradaException;
import com.synapse.crm.core.application.etapa.GestaoDeEtapasUseCases;
import com.synapse.crm.core.application.lead.AlterarEtapaDoLeadPelaAutomacaoUseCase;
import com.synapse.crm.core.application.lead.AlterarEtapaDoLeadPelaAutomacaoUseCase.ResultadoEtapaLead;
import com.synapse.crm.core.application.lead.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.core.application.lead.IdempotencyKeyInvalidaException;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.etapa.ResultadoEtapa;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Catalogo de etapas e mudanca de etapa do lead pela Automacao (E196).
 *
 * <p>{@code listar} reaproveita {@link GestaoDeEtapasUseCases#listar()} — o mesmo caso de uso da tela
 * administrativa — porque e leitura pura e a etapa e configuracao por tenant (E06a): a Automacao
 * precisa saber quais UUIDs existem antes de poder escolher um.
 */
@RestController
@RequestMapping("/internal/v1")
@Tag(
        name = "Etapas internas",
        description = "Catálogo de etapas do funil e mudança de etapa do lead pela Automação.")
@SecurityRequirement(name = "synapseToken")
class EtapasAutomacaoInternalController {

    private final GestaoDeEtapasUseCases etapas;
    private final AlterarEtapaDoLeadPelaAutomacaoUseCase alterarEtapa;

    EtapasAutomacaoInternalController(
            GestaoDeEtapasUseCases etapas, AlterarEtapaDoLeadPelaAutomacaoUseCase alterarEtapa) {
        this.etapas = etapas;
        this.alterarEtapa = alterarEtapa;
    }

    @Operation(
            summary = "Listar catálogo de etapas",
            description = "Retorna as etapas do funil na ordem configurada, para a Automação escolher um UUID válido.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Catálogo de etapas do tenant."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/etapas")
    List<EtapaCatalogoResposta> listar() {
        return ContextoDeServico.buscarComo(
                "listar-etapas-automacao",
                () -> etapas.listar().stream().map(EtapaCatalogoResposta::de).toList());
    }

    @Operation(
            summary = "Alterar a etapa do lead",
            description = "Move o lead para outra etapa do funil e publica o mesmo evento de auditoria "
                    + "que a tela administrativa publica. Idempotente por Idempotency-Key.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Etapa aplicada ou resposta do replay."),
                @ApiResponse(responseCode = "400", description = "Idempotency-Key ausente."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente."),
                @ApiResponse(responseCode = "409", description = "Chave reutilizada para outra requisição."),
                @ApiResponse(responseCode = "422", description = "etapaId não existe no catálogo do tenant.")
            })
    @PostMapping("/leads/{leadId}/etapa")
    ResultadoEtapaLead alterar(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID leadId,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody EtapaDoLeadRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "alterar-etapa-automacao",
                () -> alterarEtapa.executar(leadId, requisicao.etapaId(), chave));
    }

    @ExceptionHandler(IdempotencyKeyInvalidaException.class)
    ProblemDetail chaveInvalida(IdempotencyKeyInvalidaException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida", erro.getMessage());
    }

    @ExceptionHandler(ChaveIdempotenciaReutilizadaException.class)
    ProblemDetail chaveReutilizada(ChaveIdempotenciaReutilizadaException erro) {
        return problema(HttpStatus.CONFLICT, "Requisicao invalida", erro.getMessage());
    }

    @ExceptionHandler(LeadDaAutomacaoNaoEncontradoException.class)
    ProblemDetail leadNaoEncontrado(LeadDaAutomacaoNaoEncontradoException erro) {
        return problema(HttpStatus.NOT_FOUND, "Lead nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(EtapaNaoEncontradaException.class)
    ProblemDetail etapaNaoEncontrada(EtapaNaoEncontradaException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Etapa fora do catalogo", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record EtapaDoLeadRequisicao(
            @Schema(description = "UUID de uma etapa já existente no catálogo.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull UUID etapaId) {}

    record EtapaCatalogoResposta(UUID id, String nome, short ordem, String corVisual, ResultadoEtapa resultado) {
        static EtapaCatalogoResposta de(EtapaAtendimento etapa) {
            return new EtapaCatalogoResposta(
                    etapa.id(), etapa.nome(), etapa.ordem(), etapa.corVisual(), etapa.resultado());
        }
    }
}
