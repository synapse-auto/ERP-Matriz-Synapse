package com.synapse.crm.atendimento.interfaces;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.resumo.SolicitacaoResumoIaRepositorio;
import com.synapse.crm.atendimento.application.resumo.SolicitarResumoIaUseCase;

/** Borda autenticada do botão Gerar/Regerar; o navegador nunca acessa o n8n. */
@RestController
@RequestMapping("/api/v1/atendimentos/{atendimentoId}/resumo-ia")
@Tag(name = "Resumo por IA", description = "Solicitação assíncrona de resumo para a conversa atual.")
class ResumoIaController {

    private final SolicitarResumoIaUseCase resumo;

    ResumoIaController(SolicitarResumoIaUseCase resumo) {
        this.resumo = resumo;
    }

    @Operation(
            summary = "Solicitar geração do resumo por IA",
            description = "Cria um ciclo idempotente e o entrega ao n8n pela outbox. Não envia histórico no request.",
            responses = {
                @ApiResponse(responseCode = "202", description = "Solicitação pendente ou já em processamento."),
                @ApiResponse(responseCode = "403", description = "Atendimento não autorizado."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "503", description = "Automação não configurada.")
            })
    @PostMapping
    ResponseEntity<SolicitacaoResumoIaRepositorio.Solicitacao> solicitar(
            @PathVariable UUID atendimentoId,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String chave) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(resumo.executar(atendimentoId, parseChave(chave)));
    }

    @Operation(
            summary = "Consultar ciclo atual do resumo por IA",
            responses = {
                @ApiResponse(responseCode = "200", description = "Estado persistido; 200 com null quando ainda não houve solicitação."),
                @ApiResponse(responseCode = "403", description = "Atendimento não autorizado."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente.")
            })
    @GetMapping
    SolicitacaoResumoIaRepositorio.Solicitacao estado(@PathVariable UUID atendimentoId) {
        return resumo.estado(atendimentoId);
    }

    @ExceptionHandler(SolicitarResumoIaUseCase.ResumoIaAutomacaoDesabilitadoException.class)
    ProblemDetail automacaoDesabilitada(RuntimeException erro) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Resumo por IA indisponivel", erro.getMessage());
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail atendimentoIndisponivel(RuntimeException erro) {
        return problema(HttpStatus.NOT_FOUND, "Atendimento nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyInvalidaException.class)
    ProblemDetail chaveInvalida(RuntimeException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida", erro.getMessage());
    }

    @ExceptionHandler(SolicitarResumoIaUseCase.ResumoIaIdempotenciaIncompativelException.class)
    ProblemDetail chaveIncompativel(RuntimeException erro) {
        return problema(HttpStatus.CONFLICT, "Solicitacao de resumo incompatível", erro.getMessage());
    }

    private static UUID parseChave(String valor) {
        try {
            if (valor == null || valor.isBlank()) throw new IllegalArgumentException();
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException erro) {
            throw new IdempotencyKeyInvalidaException();
        }
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail resultado = ProblemDetail.forStatusAndDetail(status, detalhe);
        resultado.setTitle(titulo);
        return resultado;
    }
}
