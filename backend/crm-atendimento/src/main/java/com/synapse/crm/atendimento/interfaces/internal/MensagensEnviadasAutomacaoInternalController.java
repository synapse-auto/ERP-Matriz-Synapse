package com.synapse.crm.atendimento.interfaces.internal;

import java.time.Instant;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.MensagemAutomacaoInvalidaException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.RegistrarMensagemEnviadaDaAutomacaoUseCase;
import com.synapse.crm.atendimento.application.WamidJaRegistradoEmOutroAtendimentoException;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Registra no histórico uma mensagem que a Automação já enviou ao provedor. */
@RestController
@RequestMapping("/internal/v1/atendimentos")
@Tag(name = "Mensagens internas", description = "Registro idempotente de mensagens já enviadas pela Automação.")
@SecurityRequirement(name = "synapseToken")
class MensagensEnviadasAutomacaoInternalController {

    private final RegistrarMensagemEnviadaDaAutomacaoUseCase registrar;

    MensagensEnviadasAutomacaoInternalController(RegistrarMensagemEnviadaDaAutomacaoUseCase registrar) {
        this.registrar = registrar;
    }

    @Operation(
            summary = "Registrar mensagem já enviada",
            description = "Persiste a saída da Automação no histórico e publica no WebSocket; nunca chama a Meta.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagem registrada ou chamada repetida de forma idempotente."),
                @ApiResponse(responseCode = "400", description = "Mensagem normalizada inválida."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "wamid já pertence a outro atendimento.")
            })
    @PostMapping("/{id}/mensagens-enviadas")
    MensagemEnviadaResposta registrar(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Valid @RequestBody MensagemEnviadaRequisicao requisicao) {
        var entrada = new RegistrarMensagemEnviadaDaAutomacaoUseCase.Requisicao(
                requisicao.wamid(),
                requisicao.tipo(),
                requisicao.conteudo(),
                requisicao.midiaUrl(),
                requisicao.midiaMetadados());
        var resultado = ContextoDeServico.buscarComo("registro-mensagem-automacao", () -> registrar.executar(id, entrada));
        return MensagemEnviadaResposta.de(resultado);
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail aoNaoEncontrar(RecursoDeAtendimentoIndisponivelException erro) {
        return problema(HttpStatus.NOT_FOUND, "Atendimento nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(MensagemAutomacaoInvalidaException.class)
    ProblemDetail aoReceberMensagemInvalida(MensagemAutomacaoInvalidaException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Mensagem invalida", erro.getMessage());
    }

    @ExceptionHandler(WamidJaRegistradoEmOutroAtendimentoException.class)
    ProblemDetail aoRepetirWamidEmOutroAtendimento(WamidJaRegistradoEmOutroAtendimentoException erro) {
        return problema(HttpStatus.CONFLICT, "wamid ja registrado", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record MensagemEnviadaRequisicao(
            @Schema(description = "Identificador retornado pela Meta.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String wamid,
            @Schema(description = "Tipo normalizado da mensagem.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull TipoMensagem tipo,
            @Schema(description = "Texto quando tipo=TEXTO.") String conteudo,
            @Schema(description = "Referência opaca de mídia já armazenada no CRM.") String midiaUrl,
            @Schema(description = "Metadados normalizados da mídia.") String midiaMetadados) {}

    record MensagemEnviadaResposta(
            UUID atendimentoId,
            UUID mensagemId,
            String statusEntrega,
            Instant enviadoEm,
            boolean idempotente) {

        static MensagemEnviadaResposta de(RegistrarMensagemEnviadaDaAutomacaoUseCase.Resultado resultado) {
            return new MensagemEnviadaResposta(
                    resultado.atendimentoId(),
                    resultado.mensagemId(),
                    resultado.statusEntrega().name(),
                    resultado.enviadoEm(),
                    resultado.idempotente());
        }
    }
}
