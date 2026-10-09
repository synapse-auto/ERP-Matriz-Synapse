package com.synapse.crm.atendimento.interfaces.internal;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.atendimento.application.ComandosAutomacaoUseCase;
import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.informacoeschatbot.InformacoesDoChatbotDesabilitadasException;
import com.synapse.crm.atendimento.application.informacoeschatbot.InformacoesDoChatbotRecusadasException;
import com.synapse.crm.atendimento.domain.informacoeschatbot.ConteudoDasInformacoesInvalidoException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Entrega do que o chatbot coletou, depois da transferencia. Registro interno do historico: nao envia
 * nada ao WhatsApp, nao muda responsavel e nao toca no resumo da ficha do lead.
 */
@RestController
@RequestMapping("/internal/v1/atendimentos")
@Tag(name = "Atendimento interno", description = "Comandos de atendimento consumidos pela Automação.")
@SecurityRequirement(name = "synapseToken")
class InformacoesDoChatbotInternalController {

    private final ComandosAutomacaoUseCase comandos;

    InformacoesDoChatbotInternalController(ComandosAutomacaoUseCase comandos) {
        this.comandos = comandos;
    }

    @Operation(
            summary = "Registrar as informações coletadas pelo chatbot",
            description = "Grava um snapshot interno no histórico do atendimento identificado, depois que ele foi"
                    + " transferido a um humano. Não envia nada ao WhatsApp, não altera responsável, participantes"
                    + " nem o resumo da ficha do lead, e não dispara transferência. Idempotente por Idempotency-Key:"
                    + " a mesma chave com o mesmo conteúdo e atendimento devolve a resposta original; com conteúdo"
                    + " ou atendimento diferente responde 409. Com o recurso desabilitado na instância responde 409"
                    + " (motivo FUNCIONALIDADE_DESABILITADA) sem gravar nada.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Card registrado ou resposta original de um retry."),
                @ApiResponse(responseCode = "400", description = "Corpo ou Idempotency-Key ausente ou inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Chave reutilizada com outro conteúdo ou atendimento; recurso"
                        + " desabilitado (motivo FUNCIONALIDADE_DESABILITADA); atendimento ainda com a IA"
                        + " (ATENDIMENTO_NAO_TRANSFERIDO) ou já finalizado (ATENDIMENTO_FINALIZADO)."),
                @ApiResponse(responseCode = "422", description = "Conteúdo vazio, com caractere nulo ou acima do limite da instância.")
            })
    @PostMapping("/{id}/informacoes-do-chatbot")
    ComandosAutomacaoUseCase.InformacoesDoChatbotResposta registrar(
            @Parameter(description = "Identificador do atendimento transferido.", required = true) @PathVariable UUID id,
            @Parameter(description = "Chave estável da ocorrência da transferência; repetições devolvem a mesma resposta.", required = true)
                    @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody InformacoesRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "registrar-informacoes-do-chatbot",
                () -> comandos.registrarInformacoesDoChatbot(id, chave, requisicao.conteudo()));
    }

    @ExceptionHandler(IdempotencyKeyInvalidaException.class)
    ProblemDetail aoReceberChaveInvalida(IdempotencyKeyInvalidaException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida", erro.getMessage());
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException erro) {
        return problema(HttpStatus.NOT_FOUND, "Atendimento nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(ChaveIdempotenciaReutilizadaException.class)
    ProblemDetail aoReutilizarChave(ChaveIdempotenciaReutilizadaException erro) {
        return problema(HttpStatus.CONFLICT, "Operacao nao pode ser aplicada", erro.getMessage());
    }

    @ExceptionHandler(InformacoesDoChatbotDesabilitadasException.class)
    ProblemDetail aoEstarDesabilitado(InformacoesDoChatbotDesabilitadasException erro) {
        ProblemDetail problema = problema(HttpStatus.CONFLICT, "Operacao nao pode ser aplicada", erro.getMessage());
        problema.setProperty("motivo", "FUNCIONALIDADE_DESABILITADA");
        return problema;
    }

    @ExceptionHandler(InformacoesDoChatbotRecusadasException.class)
    ProblemDetail aoRecusarAtendimento(InformacoesDoChatbotRecusadasException erro) {
        ProblemDetail problema = problema(HttpStatus.CONFLICT, "Operacao nao pode ser aplicada", erro.getMessage());
        problema.setProperty("motivo", erro.motivo().name());
        return problema;
    }

    @ExceptionHandler(ConteudoDasInformacoesInvalidoException.class)
    ProblemDetail aoRecusarConteudo(ConteudoDasInformacoesInvalidoException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Conteudo invalido", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record InformacoesRequisicao(
            @Schema(
                            description = "Texto puro, com quebras de linha, coletado pelo chatbot. Sem HTML: é exibido como texto.",
                            example = "Nome: Maria\nInteresse: avaliação\nMelhor horário: manhã",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull String conteudo) {}
}
