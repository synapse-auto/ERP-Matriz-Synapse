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
import com.synapse.crm.atendimento.application.painel.ListarAtendimentosVisiveisUseCase;
import com.synapse.crm.atendimento.application.painel.ObterCartaoAtendimentoVisivelUseCase;
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

    PainelDeAtendimentosController(
            ListarAtendimentosVisiveisUseCase listar,
            ContarAtendimentosPorVisaoUseCase contarPorVisao,
            ObterCartaoAtendimentoVisivelUseCase obterCartao) {
        this.listar = listar;
        this.contarPorVisao = contarPorVisao;
        this.obterCartao = obterCartao;
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
}
