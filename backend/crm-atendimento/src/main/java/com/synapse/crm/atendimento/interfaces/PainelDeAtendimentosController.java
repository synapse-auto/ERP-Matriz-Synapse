package com.synapse.crm.atendimento.interfaces;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.application.painel.ListarAtendimentosVisiveisUseCase;
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

    PainelDeAtendimentosController(ListarAtendimentosVisiveisUseCase listar) {
        this.listar = listar;
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
}
