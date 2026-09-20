package com.synapse.crm.core.interfaces.internal;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.lead.CampoDataNascimentoNaoCadastradoException;
import com.synapse.crm.core.application.lead.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.core.application.lead.DefinirDataNascimentoDoLeadPelaAutomacaoUseCase;
import com.synapse.crm.core.application.lead.DefinirDataNascimentoDoLeadPelaAutomacaoUseCase.ResultadoDataNascimentoLead;
import com.synapse.crm.core.application.lead.IdempotencyKeyInvalidaException;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.domain.campocustomizado.DadosCustomizadosInvalidosException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Data de nascimento do lead escrita pela Automacao (E196), na mesma convencao de campo customizado
 * reservado que a E194 ja consome. Rota propria — nunca um quinto/sexto parametro do EV-05.
 */
@RestController
@RequestMapping("/internal/v1")
@Tag(
        name = "Data de nascimento do lead",
        description = "Escrita idempotente da data de nascimento lida da conversa pela Automação.")
@SecurityRequirement(name = "synapseToken")
class DataNascimentoDoLeadInternalController {

    private final DefinirDataNascimentoDoLeadPelaAutomacaoUseCase definir;

    DataNascimentoDoLeadInternalController(DefinirDataNascimentoDoLeadPelaAutomacaoUseCase definir) {
        this.definir = definir;
    }

    @Operation(
            summary = "Definir a data de nascimento do lead",
            description = "Grava em lead.dados_customizados, chave reservada 'data_nascimento' (tipo DATA). "
                    + "Só preenche se ainda estiver vazio — um valor já preenchido não é sobrescrito, "
                    + "a resposta volta com situacao=IGNORADO_JA_PREENCHIDO. Idempotente por Idempotency-Key.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Data aplicada, ou já preenchida (não sobrescrita), ou resposta do replay."),
                @ApiResponse(responseCode = "400", description = "Idempotency-Key ausente."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente, ou campo customizado 'data_nascimento' (tipo DATA) não cadastrado nesta instância."),
                @ApiResponse(responseCode = "409", description = "Chave reutilizada para outra requisição."),
                @ApiResponse(responseCode = "422", description = "Data em formato inválido.")
            })
    @PostMapping("/leads/{leadId}/data-nascimento")
    ResultadoDataNascimentoLead definir(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID leadId,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody DataNascimentoRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "definir-data-nascimento-automacao",
                () -> definir.executar(leadId, requisicao.dataNascimento(), chave));
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

    @ExceptionHandler(CampoDataNascimentoNaoCadastradoException.class)
    ProblemDetail campoNaoCadastrado(CampoDataNascimentoNaoCadastradoException erro) {
        return problema(HttpStatus.NOT_FOUND, "Campo customizado nao cadastrado", erro.getMessage());
    }

    @ExceptionHandler(DadosCustomizadosInvalidosException.class)
    ProblemDetail dataInvalida(DadosCustomizadosInvalidosException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Data de nascimento invalida", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record DataNascimentoRequisicao(
            @Schema(description = "Data de nascimento em AAAA-MM-DD.", example = "1990-05-21", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String dataNascimento) {}
}
