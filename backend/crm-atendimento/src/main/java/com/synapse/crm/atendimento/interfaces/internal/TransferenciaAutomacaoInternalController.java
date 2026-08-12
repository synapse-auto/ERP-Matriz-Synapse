package com.synapse.crm.atendimento.interfaces.internal;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.NenhumAtendenteDisponivelException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.TransferenciaDaAutomacaoInvalidaException;
import com.synapse.crm.atendimento.application.TransferirAtendimentoDaAutomacaoUseCase;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Entrada tecnica pela qual a Automacao entrega uma conversa da IA a distribuicao humana. */
@RestController
@RequestMapping("/internal/v1/atendimentos")
@Tag(
        name = "Atendimento interno",
        description = "Operacoes de atendimento consumidas pelo servico de Automacao.")
@SecurityRequirement(name = "synapseToken")
class TransferenciaAutomacaoInternalController {

    private final TransferirAtendimentoDaAutomacaoUseCase transferir;

    TransferenciaAutomacaoInternalController(TransferirAtendimentoDaAutomacaoUseCase transferir) {
        this.transferir = transferir;
    }

    @Operation(
            summary = "Distribuir atendimento da IA",
            description = "Escolhe no servidor um atendente online e disponivel, priorizando a menor carga aberta; nao aceita destinatario no corpo.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento entregue ao destino selecionado."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou invalido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Atendimento nao esta com a IA ou nao ha destino elegivel.")
            })
    @PostMapping("/{id}/transferir")
    TransferenciaResposta transferir(
            @Parameter(description = "Identificador do atendimento sob responsabilidade da IA.", required = true)
                    @PathVariable UUID id) {
        Atendimento atendimento = ContextoDeServico.buscarComo(
                "transferencia-automacao", () -> transferir.executar(id));
        return TransferenciaResposta.de(atendimento);
    }

    @ExceptionHandler({
        NenhumAtendenteDisponivelException.class,
        TransferenciaDaAutomacaoInvalidaException.class
    })
    ProblemDetail aoConflitar(RuntimeException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, erro.getMessage());
        problema.setTitle("Transferencia indisponivel");
        return problema;
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, erro.getMessage());
        problema.setTitle("Atendimento nao encontrado");
        return problema;
    }

    record TransferenciaResposta(UUID atendimentoId, UUID atendenteId, String status) {
        static TransferenciaResposta de(Atendimento atendimento) {
            return new TransferenciaResposta(
                    atendimento.id(), atendimento.atendenteId(), atendimento.status().name());
        }
    }
}
