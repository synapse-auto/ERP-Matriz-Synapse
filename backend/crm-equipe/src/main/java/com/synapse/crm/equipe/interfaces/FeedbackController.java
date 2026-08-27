package com.synapse.crm.equipe.interfaces;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.synapse.crm.equipe.application.feedback.EnviarFeedbackUseCase;
import com.synapse.crm.equipe.application.feedback.FeedbackRepositorio;
import com.synapse.crm.equipe.application.feedback.ListarFeedbacksUseCase;
import com.synapse.crm.equipe.domain.feedback.AreaFeedback;
import com.synapse.crm.equipe.domain.feedback.Feedback;
import com.synapse.crm.equipe.domain.feedback.FeedbackInvalidoException;
import com.synapse.crm.equipe.domain.feedback.TipoFeedback;

@RestController
@RequestMapping("/api/v1/feedbacks")
@Tag(name = "Feedbacks", description = "Envio de feedback interno e consulta administrativa.")
@SecurityRequirement(name = "bearerAuth")
class FeedbackController {
    private final EnviarFeedbackUseCase enviar;
    private final ListarFeedbacksUseCase listar;

    FeedbackController(EnviarFeedbackUseCase enviar, ListarFeedbacksUseCase listar) {
        this.enviar = enviar;
        this.listar = listar;
    }

    @Operation(summary = "Enviar feedback", description = "Registra sugestão ou erro com autoria derivada da sessão autenticada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Feedback registrado."),
            @ApiResponse(responseCode = "400", description = "Tipo, área ou descrição inválidos."),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FeedbackCriadoResposta enviar(@Valid @RequestBody FeedbackRequisicao requisicao) {
        return FeedbackCriadoResposta.de(
                enviar.executar(requisicao.tipo(), requisicao.areaChave(), requisicao.descricao()));
    }

    @Operation(summary = "Listar feedbacks", description = "Lista feedbacks para Administração, com filtro e paginação por cursor; exclusivo de administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de feedbacks."),
            @ApiResponse(responseCode = "400", description = "Filtro ou cursor inválido."),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado."),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso administrativo.")
    })
    @GetMapping
    PaginaFeedbackResposta listar(
            @Parameter(description = "Filtro opcional por tipo.") @RequestParam(required = false) TipoFeedback tipo,
            @Parameter(description = "Data do último item da página anterior.") @RequestParam(required = false) Instant antesDe,
            @Parameter(description = "ID do último item da página anterior.") @RequestParam(required = false) UUID antesDoId,
            @Parameter(description = "Quantidade entre 1 e 50.") @RequestParam(defaultValue = "20") int limite) {
        return PaginaFeedbackResposta.de(listar.executar(tipo, antesDe, antesDoId, limite));
    }

    @ExceptionHandler(FeedbackInvalidoException.class)
    ProblemDetail feedbackInvalido(FeedbackInvalidoException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail requisicaoInvalida(Exception ignorado) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Os dados informados para o feedback são inválidos.");
    }

    record FeedbackRequisicao(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TipoFeedback tipo,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AreaFeedback areaChave,
            @NotBlank @Size(max = Feedback.LIMITE_DESCRICAO)
                    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = Feedback.LIMITE_DESCRICAO)
                    String descricao) {}

    record FeedbackCriadoResposta(UUID id, TipoFeedback tipo, String areaChave,
            String descricao, Instant criadoEm) {
        static FeedbackCriadoResposta de(Feedback feedback) {
            return new FeedbackCriadoResposta(feedback.id(), feedback.tipo(),
                    feedback.area().name(), feedback.descricao(), feedback.criadoEm());
        }
    }

    record FeedbackResposta(UUID id, UUID autorId, String autorNome, String autorPapel,
            String autorFotoUrl, TipoFeedback tipo, String areaChave, String descricao,
            Instant criadoEm) {
        static FeedbackResposta de(FeedbackRepositorio.FeedbackResumo feedback) {
            return new FeedbackResposta(feedback.id(), feedback.autorId(), feedback.autorNome(),
                    feedback.autorPapel().name(), feedback.autorFotoUrl(), feedback.tipo(),
                    feedback.areaChave(), feedback.descricao(), feedback.criadoEm());
        }
    }

    record PaginaFeedbackResposta(List<FeedbackResposta> itens, Instant proximoCriadoEm,
            UUID proximoId) {
        static PaginaFeedbackResposta de(FeedbackRepositorio.Pagina pagina) {
            return new PaginaFeedbackResposta(
                    pagina.itens().stream().map(FeedbackResposta::de).toList(),
                    pagina.proximoCriadoEm(), pagina.proximoId());
        }
    }
}
