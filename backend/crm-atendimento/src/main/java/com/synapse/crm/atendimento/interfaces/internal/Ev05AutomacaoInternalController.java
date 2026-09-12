package com.synapse.crm.atendimento.interfaces.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

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
import com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.internal.CandidatosEv05Repositorio;
import com.synapse.crm.atendimento.application.internal.ContextoEv05UseCase;
import com.synapse.crm.atendimento.application.internal.Ev05LeadSemAtendimentoException;
import com.synapse.crm.atendimento.application.internal.Ev05LeadUseCase;
import com.synapse.crm.atendimento.application.internal.Ev05ResumoInvalidoException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Contrato /internal/v1 do ciclo EV-05 consumido pelo cron do n8n. */
@RestController
@Validated
@RequestMapping("/internal/v1/ev05")
@Tag(name = "EV-05 Automação", description = "Candidatos, contexto e escritas idempotentes de IA.")
@SecurityRequirement(name = "synapseToken")
class Ev05AutomacaoInternalController {
    private final CandidatosEv05Repositorio candidatos;
    private final ContextoEv05UseCase contexto;
    private final Ev05LeadUseCase leads;
    private final int tamanhoMaximo;

    Ev05AutomacaoInternalController(
            CandidatosEv05Repositorio candidatos,
            ContextoEv05UseCase contexto,
            Ev05LeadUseCase leads,
            @Value("${synapse.suporte.tamanho-pagina}") int tamanhoMaximo) {
        this.candidatos = candidatos;
        this.contexto = contexto;
        this.leads = leads;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    @Operation(
            summary = "Listar candidatos do EV-05",
            description = "Retorna somente IDs de atendimentos EM_ATENDIMENTO e seu marco de atividade. "
                    + "Não inclui telefone, mensagens, notas, resumo ou dados customizados.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Página de candidatos."),
                @ApiResponse(responseCode = "400", description = "Página ou tamanho inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/candidatos")
    CandidatosResposta candidatos(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) int tamanho,
            @RequestParam(required = false) Instant atualizadoDesde) {
        return ContextoDeServico.buscarComo(
                "listar-candidatos-ev05", () -> {
                    int efetivo = Math.min(tamanho, tamanhoMaximo);
                    return CandidatosResposta.de(candidatos.listar(pagina, efetivo, atualizadoDesde));
                });
    }

    @Operation(
            summary = "Consultar contexto de conversa do EV-05",
            description = "Retorna histórico limitado do atendimento EM_ATENDIMENTO, sem URL de mídia "
                    + "ou payload de provedor. O campo contextoAte deve ser enviado na escrita do resumo "
                    + "para impedir resultados tardios.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Contexto limitado."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou inelegível.")
            })
    @GetMapping("/atendimentos/{atendimentoId}/contexto")
    ContextoEv05UseCase.Resposta contexto(
            @Parameter(required = true) @PathVariable UUID atendimentoId) {
        return ContextoDeServico.buscarComo("consultar-contexto-ev05", () -> contexto.executar(atendimentoId));
    }

    @Operation(
            summary = "Consultar situação do resumo do lead",
            responses = {
                @ApiResponse(responseCode = "200", description = "Situação atual do resumo."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead sem atendimento EM_ATENDIMENTO ou inexistente.")
            })
    @GetMapping("/leads/{leadId}/resumo")
    Ev05LeadUseCase.EstadoResumo resumo(@PathVariable UUID leadId) {
        return ContextoDeServico.buscarComo("consultar-resumo-ev05", () -> leads.estadoResumo(leadId));
    }

    @Operation(
            summary = "Gravar resumo do lead de forma idempotente",
            responses = {
                @ApiResponse(responseCode = "200", description = "Resumo gravado ou resposta do replay."),
                @ApiResponse(responseCode = "400", description = "Idempotency-Key ou corpo inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead sem atendimento EM_ATENDIMENTO ou inexistente."),
                @ApiResponse(responseCode = "409", description = "Chave reutilizada para outra operação/atendimento."),
                @ApiResponse(responseCode = "422", description = "Resumo vazio, grande ou baseado em contexto obsoleto.")
            })
    @PostMapping("/leads/{leadId}/resumo")
    Ev05LeadUseCase.ResultadoResumo gravarResumo(
            @PathVariable UUID leadId,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody ResumoRequisicao requisicao) {
        validarLeadDoCorpo(leadId, requisicao.leadId());
        return ContextoDeServico.buscarComo(
                "gravar-resumo-ev05", () -> leads.gravarResumo(leadId, requisicao.resumo(), requisicao.contextoGeradoEm(), chave));
    }

    @Operation(
            summary = "Consultar situação do preenchimento automático",
            responses = {
                @ApiResponse(responseCode = "200", description = "Situação dos campos alvo."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead sem atendimento EM_ATENDIMENTO ou inexistente.")
            })
    @GetMapping("/leads/{leadId}/preenchimento")
    Ev05LeadUseCase.EstadoPreenchimento preenchimento(@PathVariable UUID leadId) {
        return ContextoDeServico.buscarComo("consultar-preenchimento-ev05", () -> leads.estadoPreenchimento(leadId));
    }

    @Operation(
            summary = "Aplicar preenchimento automático do lead",
            description = "Preenche somente campos vazios e válidos. A avaliação é marcada mesmo quando "
                    + "nenhum campo pode ser aplicado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Campos avaliados ou resposta do replay."),
                @ApiResponse(responseCode = "400", description = "Idempotency-Key ou corpo inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead sem atendimento EM_ATENDIMENTO ou inexistente."),
                @ApiResponse(responseCode = "409", description = "Chave reutilizada para outra operação/atendimento.")
            })
    @PostMapping("/leads/{leadId}/preenchimento")
    Ev05LeadUseCase.ResultadoPreenchimento preencher(
            @PathVariable UUID leadId,
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody PreenchimentoRequisicao requisicao) {
        validarLeadDoCorpo(leadId, requisicao.leadId());
        return ContextoDeServico.buscarComo(
                "gravar-preenchimento-ev05",
                () -> leads.preencher(
                        leadId, requisicao.email(), requisicao.cpf(), requisicao.empresa(),
                        requisicao.localizacao(), chave));
    }

    private static void validarLeadDoCorpo(UUID path, UUID corpo) {
        if (corpo != null && !path.equals(corpo)) {
            throw new IllegalArgumentException("leadId do corpo difere do caminho");
        }
    }

    @ExceptionHandler(IdempotencyKeyInvalidaException.class)
    ProblemDetail chaveInvalida(IdempotencyKeyInvalidaException erro) {
        return problema(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida", erro.getMessage());
    }

    @ExceptionHandler({ChaveIdempotenciaReutilizadaException.class, IllegalArgumentException.class})
    ProblemDetail conflito(RuntimeException erro) {
        return problema(HttpStatus.CONFLICT, "Requisicao EV-05 invalida", erro.getMessage());
    }

    @ExceptionHandler({
        RecursoDeAtendimentoIndisponivelException.class,
        Ev05LeadSemAtendimentoException.class,
        com.synapse.crm.core.application.lead.LeadEv05NaoEncontradoException.class
    })
    ProblemDetail naoEncontrado(RuntimeException erro) {
        return problema(HttpStatus.NOT_FOUND, "Recurso EV-05 nao encontrado", erro.getMessage());
    }

    @ExceptionHandler({Ev05ResumoInvalidoException.class, com.synapse.crm.core.application.lead.EscritaEv05ObsoletaException.class})
    ProblemDetail dadosInvalidos(RuntimeException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Dados EV-05 recusados", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail resultado = ProblemDetail.forStatusAndDetail(status, detalhe);
        resultado.setTitle(titulo);
        return resultado;
    }

    record ResumoRequisicao(
            @Schema(description = "Identificador do lead; opcional quando já está no caminho.") UUID leadId,
            @Schema(description = "Resumo conciso.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String resumo,
            @Schema(description = "Marco retornado por /contexto, para proteção contra ciclo tardio.") Instant contextoGeradoEm) {}

    record PreenchimentoRequisicao(
            UUID leadId, String email, String cpf, String empresa, String localizacao) {}

    record CandidatosResposta(List<ItemResposta> itens, int pagina, int tamanho, boolean temMais) {
        static CandidatosResposta de(CandidatosEv05Repositorio.Pagina pagina) {
            return new CandidatosResposta(
                    pagina.itens().stream().map(ItemResposta::de).toList(),
                    pagina.pagina(), pagina.tamanho(), pagina.temMais());
        }
    }

    record ItemResposta(UUID atendimentoId, UUID leadId, String situacao, Instant atualizadoEm) {
        static ItemResposta de(CandidatosEv05Repositorio.Item item) {
            return new ItemResposta(item.atendimentoId(), item.leadId(), item.situacao(), item.atualizadoEm());
        }
    }
}
