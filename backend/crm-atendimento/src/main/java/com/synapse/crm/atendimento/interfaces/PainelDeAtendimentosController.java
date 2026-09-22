package com.synapse.crm.atendimento.interfaces;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.application.painel.ContarAtendimentosPorVisaoUseCase;
import com.synapse.crm.atendimento.application.painel.EstadoAtendimentoSelecionado;
import com.synapse.crm.atendimento.application.painel.ListarAtendimentosVisiveisUseCase;
import com.synapse.crm.atendimento.application.painel.ObterCartaoAtendimentoPorLeadOuTelefoneUseCase;
import com.synapse.crm.atendimento.application.painel.ObterCartaoAtendimentoVisivelUseCase;
import com.synapse.crm.atendimento.application.painel.ObterEstadoAtendimentoSelecionadoUseCase;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;

/**
 * A lista de conversas da tela de Atendimentos. O recorte por papel acontece no caso de uso, nunca
 * aqui — este controller so pede a visao e devolve o que veio.
 */
@RestController
@RequestMapping("/api/v1/atendimentos")
@Tag(name = "Painel de atendimentos", description = "Conversas visíveis agrupadas pela visão operacional.")
@SecurityRequirement(name = "bearerAuth")
class PainelDeAtendimentosController {

    private final ListarAtendimentosVisiveisUseCase listar;
    private final ContarAtendimentosPorVisaoUseCase contarPorVisao;
    private final ObterCartaoAtendimentoVisivelUseCase obterCartao;
    private final ObterCartaoAtendimentoPorLeadOuTelefoneUseCase obterCartaoPorLeadOuTelefone;
    private final ObterEstadoAtendimentoSelecionadoUseCase obterEstado;

    PainelDeAtendimentosController(
            ListarAtendimentosVisiveisUseCase listar,
            ContarAtendimentosPorVisaoUseCase contarPorVisao,
            ObterCartaoAtendimentoVisivelUseCase obterCartao,
            ObterCartaoAtendimentoPorLeadOuTelefoneUseCase obterCartaoPorLeadOuTelefone,
            ObterEstadoAtendimentoSelecionadoUseCase obterEstado) {
        this.listar = listar;
        this.contarPorVisao = contarPorVisao;
        this.obterCartao = obterCartao;
        this.obterCartaoPorLeadOuTelefone = obterCartaoPorLeadOuTelefone;
        this.obterEstado = obterEstado;
    }

    @Operation(
            summary = "Listar atendimentos visíveis",
            description = "Retorna os cartões da visão solicitada após aplicar a visibilidade do papel autenticado.",
            responses = @ApiResponse(responseCode = "200", description = "Cartões da visão solicitada."))
    @GetMapping
    List<CartaoAtendimento> listar(
            @Parameter(description = "Grupo operacional do painel.", required = true)
                    @RequestParam VisaoAtendimento visao) {
        return listar.executar(visao);
    }

    @Operation(
            summary = "Obter cartão de atendimento visível",
            description = "Resolve o atendimento já autorizado sem depender da visão ou dos filtros ativos da lista.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Cartão do atendimento."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")
            })
    @GetMapping("/{atendimentoId}/cartao")
    CartaoAtendimento obterCartao(@PathVariable UUID atendimentoId) {
        return obterCartao.executar(atendimentoId);
    }

    @Operation(
            summary = "Obter estado canonico do atendimento selecionado",
            description = "Snapshot versionado que governa responsável, participantes, status e permissão do composer.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Estado atual e autorizado do atendimento."),
                @ApiResponse(responseCode = "404", description = "Atendimento inexistente ou não visível.")
            })
    @GetMapping("/{atendimentoId}/estado")
    EstadoAtendimentoSelecionado obterEstado(@PathVariable UUID atendimentoId) {
        return obterEstado.executar(atendimentoId);
    }

    @Operation(
            summary = "Buscar atendimento por lead ou telefone",
            description = "Retorna o cartão representativo mais recente do lead visível. Informe exatamente um dos parâmetros; telefone é normalizado pelo mesmo contrato do cadastro.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Cartão do atendimento."),
                @ApiResponse(responseCode = "400", description = "Informe exatamente leadId ou telefone."),
                @ApiResponse(responseCode = "404", description = "Lead ou atendimento inexistente ou não visível.")
            })
    @GetMapping("/busca")
    CartaoAtendimento buscar(
            @Parameter(description = "Identificador do lead.") @RequestParam(required = false) UUID leadId,
            @Parameter(description = "Telefone em qualquer formato aceito pelo cadastro.")
                    @RequestParam(required = false) String telefone) {
        return obterCartaoPorLeadOuTelefone.executar(leadId, telefone);
    }

    /**
     * Os badges das abas (E17b §Bloco 6): uma contagem por visão, na mesma chamada, para a tela não
     * disparar uma requisição por aba.
     */
    @Operation(
            summary = "Contar atendimentos por visão",
            description = "Retorna, para cada visão operacional, quantos cartões o papel autenticado enxergaria — a mesma visibilidade da listagem.",
            responses = @ApiResponse(responseCode = "200", description = "Contagem por visão."))
    @GetMapping("/contagem")
    Map<VisaoAtendimento, Long> contagem() {
        return contarPorVisao.executar();
    }

    @ExceptionHandler(RecursoDeAtendimentoIndisponivelException.class)
    ProblemDetail naoEncontrado(RecursoDeAtendimentoIndisponivelException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, erro.getMessage());
        problema.setTitle("Nao encontrado");
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail pedidoInvalido(IllegalArgumentException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
        problema.setTitle("Requisicao invalida");
        return problema;
    }
}
