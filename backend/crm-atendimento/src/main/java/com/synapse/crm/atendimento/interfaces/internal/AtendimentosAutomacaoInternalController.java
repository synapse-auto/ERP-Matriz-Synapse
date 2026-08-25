package com.synapse.crm.atendimento.interfaces.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.atendimento.application.ComandosAutomacaoUseCase;
import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.internal.AtendimentoSemResponsavelException;
import com.synapse.crm.atendimento.application.internal.AtendimentosEmAndamentoRepositorio;
import com.synapse.crm.atendimento.application.internal.AtualizarResumoIaDoAtendimentoUseCase;
import com.synapse.crm.atendimento.application.internal.ListarAtendimentosEmAndamentoUseCase;
import com.synapse.crm.atendimento.application.internal.PeriodoDeAtividadeInvalidoException;
import com.synapse.crm.atendimento.application.internal.ResumoIaMuitoLongoException;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Consultas e escritas auxiliares consumidas pelo orquestrador da Automacao. */
@RestController
@Validated
@RequestMapping("/internal/v1/atendimentos")
@Tag(
        name = "Atendimentos internos",
        description = "Read model mínimo e comandos auxiliares da Automação sobre atendimentos.")
@SecurityRequirement(name = "synapseToken")
class AtendimentosAutomacaoInternalController {

    private final ListarAtendimentosEmAndamentoUseCase listar;
    private final ComandosAutomacaoUseCase comandos;
    private final AtualizarResumoIaDoAtendimentoUseCase atualizarResumo;
    private final int tamanhoMaximoDaPagina;

    AtendimentosAutomacaoInternalController(
            ListarAtendimentosEmAndamentoUseCase listar,
            ComandosAutomacaoUseCase comandos,
            AtualizarResumoIaDoAtendimentoUseCase atualizarResumo,
            @Value("${synapse.suporte.tamanho-pagina}") int tamanhoMaximoDaPagina) {
        this.listar = listar;
        this.comandos = comandos;
        this.atualizarResumo = atualizarResumo;
        this.tamanhoMaximoDaPagina = tamanhoMaximoDaPagina;
    }

    @Operation(
            summary = "Listar atendimentos em andamento",
            description = "Lista EM_IA e EM_ATENDIMENTO com responsável e instante da última mensagem. O filtro usa a última mensagem ou, quando ainda não há mensagem, o início do atendimento. Nunca devolve histórico nem conteúdo.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Página de atendimentos não finalizados."),
                @ApiResponse(responseCode = "400", description = "Página, tamanho ou período inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/em-andamento")
    PaginaResposta listar(
            @Parameter(description = "Início inclusivo do recorte pela última atividade, em UTC.")
                    @RequestParam(required = false) Instant atividadeDesde,
            @Parameter(description = "Fim inclusivo do recorte pela última atividade, em UTC.")
                    @RequestParam(required = false) Instant atividadeAte,
            @Parameter(description = "Índice da página, começando em zero.", example = "0")
                    @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @Parameter(description = "Quantidade solicitada; valores acima do teto da instância são reduzidos no servidor.", example = "20")
                    @RequestParam(defaultValue = "20") @Min(1) int tamanho) {
        int tamanhoEfetivo = Math.min(tamanho, tamanhoMaximoDaPagina);
        return ContextoDeServico.buscarComo(
                "listar-atendimentos-automacao",
                () -> PaginaResposta.de(
                        listar.executar(atividadeDesde, atividadeAte, pagina, tamanhoEfetivo)));
    }

    @Operation(
            summary = "Criar lembrete para o responsável",
            description = "Cria idempotentemente um lembrete automático pertencente ao atendente responsável pelo atendimento no instante da chamada. Não aceita destinatário no corpo.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Lembrete criado ou mesma resposta de um retry."),
                @ApiResponse(responseCode = "400", description = "Corpo ou Idempotency-Key inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "409", description = "Atendimento sem responsável ou chave reutilizada com outro comando.")
            })
    @PostMapping("/{id}/lembretes")
    ComandosAutomacaoUseCase.LembreteResposta criarLembrete(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Parameter(description = "Chave única da operação; o retry devolve a mesma resposta.", required = true)
                    @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody LembreteRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "criar-lembrete-automacao",
                () -> comandos.criarLembrete(id, chave, requisicao.texto(), requisicao.dataHora()));
    }

    @Operation(
            summary = "Sobrescrever resumo da IA",
            description = "Substitui o resumo_ia do lead associado ao atendimento. A ficha do lead passa a devolver o novo valor imediatamente.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Resumo sobrescrito."),
                @ApiResponse(responseCode = "400", description = "Corpo inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente."),
                @ApiResponse(responseCode = "422", description = "Resumo excede o limite configurado da instância.")
            })
    @PostMapping("/{id}/resumo")
    AtualizarResumoIaDoAtendimentoUseCase.Resultado atualizarResumo(
            @Parameter(description = "Identificador do atendimento.", required = true) @PathVariable UUID id,
            @Valid @RequestBody ResumoRequisicao requisicao) {
        return ContextoDeServico.buscarComo(
                "atualizar-resumo-automacao",
                () -> atualizarResumo.executar(id, requisicao.resumo()));
    }

    @ExceptionHandler(PeriodoDeAtividadeInvalidoException.class)
    ProblemDetail periodoInvalido(PeriodoDeAtividadeInvalidoException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Periodo de atividade invalido", erro.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyInvalidaException.class)
    ProblemDetail chaveInvalida(IdempotencyKeyInvalidaException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida", erro.getMessage());
    }

    @ExceptionHandler(ChaveIdempotenciaReutilizadaException.class)
    ProblemDetail chaveReutilizada(ChaveIdempotenciaReutilizadaException erro) {
        return problema(HttpStatus.CONFLICT, "Idempotency-Key reutilizada", erro.getMessage());
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail atendimentoNaoEncontrado(RecursoDeAtendimentoIndisponivelException erro) {
        return problema(HttpStatus.NOT_FOUND, "Atendimento nao encontrado", erro.getMessage());
    }

    @ExceptionHandler(AtendimentoSemResponsavelException.class)
    ProblemDetail atendimentoSemResponsavel(AtendimentoSemResponsavelException erro) {
        return problema(HttpStatus.CONFLICT, "Atendimento sem responsavel", erro.getMessage());
    }

    @ExceptionHandler(ResumoIaMuitoLongoException.class)
    ProblemDetail resumoMuitoLongo(ResumoIaMuitoLongoException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Resumo da IA muito longo", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }

    record LembreteRequisicao(
            @Schema(description = "Texto do lembrete.", example = "Retornar com o orçamento", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String texto,
            @Schema(description = "Data e hora do lembrete em UTC.", example = "2026-08-26T13:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull Instant dataHora) {}

    record ResumoRequisicao(
            @Schema(description = "Resumo integral que substituirá o anterior.", example = "Cliente solicitou orçamento e aguarda medidas.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String resumo) {}

    record ResponsavelResposta(UUID id, String nome) {
        static ResponsavelResposta de(AtendimentosEmAndamentoRepositorio.Responsavel responsavel) {
            return responsavel == null ? null : new ResponsavelResposta(responsavel.id(), responsavel.nome());
        }
    }

    record AtendimentoResposta(
            UUID atendimentoId,
            UUID leadId,
            StatusAtendimento status,
            ResponsavelResposta responsavel,
            Instant ultimaMensagemEm) {
        static AtendimentoResposta de(AtendimentosEmAndamentoRepositorio.Item item) {
            return new AtendimentoResposta(
                    item.atendimentoId(),
                    item.leadId(),
                    item.status(),
                    ResponsavelResposta.de(item.responsavel()),
                    item.ultimaMensagemEm());
        }
    }

    record PaginaResposta(List<AtendimentoResposta> atendimentos, int pagina, int tamanho, boolean temMais) {
        static PaginaResposta de(AtendimentosEmAndamentoRepositorio.Pagina pagina) {
            return new PaginaResposta(
                    pagina.atendimentos().stream().map(AtendimentoResposta::de).toList(),
                    pagina.pagina(),
                    pagina.tamanho(),
                    pagina.temMais());
        }
    }
}
