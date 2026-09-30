package com.synapse.crm.atendimento.interfaces.internal;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.internal.AtendimentoDaAutomacaoRepositorio.Vinculo;
import com.synapse.crm.atendimento.application.internal.ResolverAtendimentoDaAutomacaoUseCase;
import com.synapse.crm.atendimento.application.internal.ResolverAtendimentoDaAutomacaoUseCase.ResultadoDoLead;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Resolucao estreita do atendimento para a Automacao (docs/50). */
@RestController
@Validated
@RequestMapping("/internal/v1")
@Tag(name = "Atendimento da Automação", description = "Qual atendimento a Automação deve usar, sem paginação.")
@SecurityRequirement(name = "synapseToken")
class AtendimentoDaAutomacaoInternalController {

    /** Tempo sugerido ao n8n antes de consultar de novo uma entrada ainda nao registrada. */
    static final String SEGUNDOS_PARA_NOVA_CONSULTA = "2";

    private static final int TAMANHO_MAXIMO_DO_ID_EXTERNO = 256;

    private final ResolverAtendimentoDaAutomacaoUseCase resolver;

    AtendimentoDaAutomacaoInternalController(ResolverAtendimentoDaAutomacaoUseCase resolver) {
        this.resolver = resolver;
    }

    @Operation(
            summary = "Atendimento da mensagem recebida",
            description = "Devolve o atendimento em que o CRM registrou a mensagem recebida do cliente com este id do provedor. "
                    + "É a âncora da resposta ao evento: nunca devolve atendimento de outro lead nem id antigo. "
                    + "Se o atendimento já foi finalizado, ativo=false e a Automação não deve responder nele. "
                    + "404 com Retry-After enquanto o CRM ainda não processou a entrada; consulte de novo um número LIMITADO de vezes.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagem registrada; atendimento resolvido."),
                @ApiResponse(responseCode = "400", description = "Identificador inválido."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Mensagem recebida ainda não registrada (ou id que não é de entrada).")
            })
    // Query string, nao path: ids do provedor podem conter "/" (base64), que quebraria a rota.
    @GetMapping("/mensagens-recebidas/atendimento")
    AtendimentoResolvido porMensagemRecebida(
            @Parameter(description = "Id da mensagem RECEBIDA no provedor (ex.: wamid da Meta).", required = true)
                    @RequestParam
                    String idExterno) {
        if (idExterno.isBlank() || idExterno.length() > TAMANHO_MAXIMO_DO_ID_EXTERNO) {
            throw new IdExternoInvalido();
        }
        return ContextoDeServico.buscarComo("resolver-atendimento-da-entrada", () -> resolver.porMensagemRecebida(idExterno))
                .map(AtendimentoResolvido::de)
                .orElseThrow(AindaNaoRegistrada::new);
    }

    @Operation(
            summary = "Atendimento aberto do lead",
            description = "Devolve o atendimento EM_IA ou EM_ATENDIMENTO mais recente do lead, sem depender de paginação. "
                    + "Atendimento finalizado nunca é devolvido.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atendimento aberto encontrado."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido."),
                @ApiResponse(responseCode = "404", description = "Lead inexistente ou sem atendimento aberto (o título diferencia).")
            })
    @GetMapping("/leads/{leadId}/atendimento-ativo")
    AtendimentoResolvido abertoDoLead(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID leadId) {
        ResultadoDoLead resultado =
                ContextoDeServico.buscarComo("resolver-atendimento-do-lead", () -> resolver.abertoDoLead(leadId));
        return switch (resultado) {
            case ResultadoDoLead.Aberto aberto -> AtendimentoResolvido.de(aberto.vinculo());
            case ResultadoDoLead.SemAtendimentoAberto semAberto -> throw new SemAtendimentoAberto();
            case ResultadoDoLead.LeadInexistente inexistente -> throw new LeadInexistente();
        };
    }

    @ExceptionHandler(AindaNaoRegistrada.class)
    ResponseEntity<ProblemDetail> aoNaoEncontrarEntrada() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "O CRM ainda nao registrou esta mensagem recebida. Consulte de novo apos Retry-After, com limite de tentativas.");
        problema.setTitle("Mensagem recebida ainda nao registrada");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.RETRY_AFTER, SEGUNDOS_PARA_NOVA_CONSULTA)
                .body(problema);
    }

    @ExceptionHandler(IdExternoInvalido.class)
    ProblemDetail aoReceberIdInvalido() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "idExterno e obrigatorio e tem no maximo 256 caracteres.");
        problema.setTitle("Identificador invalido");
        return problema;
    }

    @ExceptionHandler(SemAtendimentoAberto.class)
    ProblemDetail aoNaoHaverAberto() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "O lead nao tem atendimento EM_IA nem EM_ATENDIMENTO.");
        problema.setTitle("Lead sem atendimento aberto");
        return problema;
    }

    @ExceptionHandler(LeadInexistente.class)
    ProblemDetail aoNaoExistirLead() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Lead inexistente.");
        problema.setTitle("Lead inexistente");
        return problema;
    }

    record AtendimentoResolvido(UUID atendimentoId, UUID leadId, String status, boolean ativo) {
        static AtendimentoResolvido de(Vinculo vinculo) {
            return new AtendimentoResolvido(
                    vinculo.atendimentoId(), vinculo.leadId(), vinculo.status().name(), vinculo.status().estaAberto());
        }
    }

    static final class IdExternoInvalido extends RuntimeException {}

    static final class AindaNaoRegistrada extends RuntimeException {}

    static final class SemAtendimentoAberto extends RuntimeException {}

    static final class LeadInexistente extends RuntimeException {}
}
