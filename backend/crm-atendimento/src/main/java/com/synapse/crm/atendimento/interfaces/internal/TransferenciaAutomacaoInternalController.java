package com.synapse.crm.atendimento.interfaces.internal;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.AtendenteDestinoInvalidoException;
import com.synapse.crm.atendimento.application.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.atendimento.application.ComandosAutomacaoUseCase;
import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.MensagemAutomacaoInvalidaException;
import com.synapse.crm.atendimento.application.NenhumAtendenteDisponivelException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.RespostaAutomacaoInvalidaException;
import com.synapse.crm.atendimento.application.TransferenciaDaAutomacaoInvalidaException;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Comandos síncronos do n8n; todos os efeitos permanecem no caso de uso e na outbox. */
@RestController
@RequestMapping("/internal/v1/atendimentos")
@Tag(name = "Atendimento interno", description = "Comandos de atendimento consumidos pela Automação.")
@SecurityRequirement(name = "synapseToken")
class TransferenciaAutomacaoInternalController {

    private final ComandosAutomacaoUseCase comandos;

    TransferenciaAutomacaoInternalController(ComandosAutomacaoUseCase comandos) {
        this.comandos = comandos;
    }

    @Operation(
            summary = "Responder como IA",
            description = "Registra uma mensagem da IA e sua intenção de envio na outbox, sem chamar o provedor neste request e sem alterar o responsável.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagem aceita para entrega assíncrona."),
                @ApiResponse(responseCode = "400", description = "Conteúdo ou Idempotency-Key ausente."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Atendimento não está sob responsabilidade da IA ou chave reutilizada."),
                @ApiResponse(responseCode = "422", description = "Canal fora da janela de texto livre.")
            })
    @PostMapping("/{id}/responder")
    ComandosAutomacaoUseCase.RespostaComandoAutomacao responder(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(description = "Chave única da operação; repetições devolvem a mesma resposta.", required = true)
                    @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody ResponderRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "responder-automacao", () -> comandos.resposta(id, chave, requisicao.conteudo()));
    }

    @Operation(
            summary = "Transferir para atendente",
            description = "Transfere um atendimento da IA para o atendente ativo informado. Gestores, subgestores e IA não são destinos aceitos.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento transferido."),
                @ApiResponse(responseCode = "400", description = "Corpo ou Idempotency-Key inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Atendimento não está sob responsabilidade da IA ou chave reutilizada."),
                @ApiResponse(responseCode = "422", description = "Destino inexistente, inativo ou com papel diferente de ATENDENTE.")
            })
    @PostMapping("/{id}/transferir")
    ComandosAutomacaoUseCase.TransferenciaResposta transferir(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(description = "Chave única da operação; repetições devolvem a mesma resposta.", required = true)
                    @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody TransferirRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "transferir-automacao", () -> comandos.transferir(id, chave, requisicao.atendenteId()));
    }

    @Operation(
            summary = "Devolver atendimento para a IA",
            description = "Remove o responsável humano e retorna o lead ao grupo IA, identificando a Automação na timeline e na auditoria.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento devolvido para a IA."),
                @ApiResponse(responseCode = "400", description = "Idempotency-Key ausente ou inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Atendimento finalizado ou chave reutilizada.")
            })
    @PatchMapping("/{id}/modo-ia")
    ComandosAutomacaoUseCase.TransferenciaResposta modoIa(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(description = "Chave única da operação; repetições devolvem a mesma resposta.", required = true)
                    @RequestHeader("Idempotency-Key") String chave,
            @RequestBody(required = false) ModoIaRequisicao ignorado) {
        return ContextoDeServico.buscarComo("modo-ia-automacao", () -> comandos.modoIa(id, chave));
    }

    @Operation(
            summary = "Transferir para o próximo humano",
            description = "Seleciona no servidor o primeiro atendente disponível por menor carga, maior tempo desde o último recebimento e id; não aceita destinatário no corpo.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento transferido."),
                @ApiResponse(responseCode = "400", description = "Idempotency-Key ausente ou inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Nenhum atendente elegível, atendimento inválido ou chave reutilizada.")
            })
    @PostMapping("/{id}/transferir-proximo-humano")
    ComandosAutomacaoUseCase.TransferenciaResposta transferirProximoHumano(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(description = "Chave única da operação; repetições devolvem a mesma resposta.", required = true)
                    @RequestHeader("Idempotency-Key") String chave) {
        return ContextoDeServico.buscarComo(
                "transferir-proximo-humano", () -> comandos.transferirProximoHumano(id, chave));
    }

    @ExceptionHandler({IdempotencyKeyInvalidaException.class, MensagemAutomacaoInvalidaException.class})
    ProblemDetail aoReceberRequisicaoInvalida(RuntimeException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida", erro.getMessage());
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException erro) {
        return problema(HttpStatus.NOT_FOUND, "Atendimento nao encontrado", erro.getMessage());
    }

    @ExceptionHandler({
        ChaveIdempotenciaReutilizadaException.class,
        NenhumAtendenteDisponivelException.class,
        TransferenciaDaAutomacaoInvalidaException.class,
        RespostaAutomacaoInvalidaException.class,
        AtendimentoJaFinalizadoException.class
    })
    ProblemDetail aoConflitar(RuntimeException erro) {
        return problema(HttpStatus.CONFLICT, "Operacao nao pode ser aplicada", erro.getMessage());
    }

    @ExceptionHandler(AtendenteDestinoInvalidoException.class)
    ProblemDetail aoRecusarDestino(AtendenteDestinoInvalidoException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Destino invalido", erro.getMessage());
    }

    @ExceptionHandler(ForaDaJanelaException.class)
    ProblemDetail aoEstarForaDaJanela(ForaDaJanelaException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Fora da janela de texto livre", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record ResponderRequisicao(
            @Schema(description = "Texto enviado pela IA.", example = "Posso ajudar com seu orçamento?", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String conteudo) {}

    record TransferirRequisicao(
            @Schema(description = "UUID de um usuário ativo com papel ATENDENTE.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull UUID atendenteId) {}

    record ModoIaRequisicao() {}
}
