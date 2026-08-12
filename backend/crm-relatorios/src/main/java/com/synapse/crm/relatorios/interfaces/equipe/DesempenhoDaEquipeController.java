package com.synapse.crm.relatorios.interfaces.equipe;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.relatorios.application.equipe.ObterDesempenhoDaEquipeUseCase;
import com.synapse.crm.relatorios.domain.equipe.DesempenhoDaEquipe;

/** Read model gerencial da tela de Equipe. */
@RestController
@RequestMapping("/api/v1/equipe/desempenho")
@Tag(
        name = "Desempenho da equipe",
        description = "Atendimentos e vendas fechadas por integrante para a gestao.")
@SecurityRequirement(name = "bearerAuth")
class DesempenhoDaEquipeController {

    private final ObterDesempenhoDaEquipeUseCase obter;

    DesempenhoDaEquipeController(ObterDesempenhoDaEquipeUseCase obter) {
        this.obter = obter;
    }

    @Operation(
            summary = "Obter desempenho da equipe",
            description = "Retorna contadores acumulados de atendimentos e vendas, restritos aos papeis de gestao.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Desempenho por integrante."),
                @ApiResponse(responseCode = "403", description = "Usuario sem papel de gestao.")
            })
    @GetMapping
    Resposta obter() {
        return Resposta.de(obter.executar());
    }

    record PorAtendente(
            UUID atendenteId, String atendenteNome, long atendimentos, long vendas) {
        static PorAtendente de(DesempenhoDaEquipe.DesempenhoPorAtendente item) {
            return new PorAtendente(
                    item.atendenteId(), item.atendenteNome(), item.atendimentos(), item.vendas());
        }
    }

    record Resposta(List<PorAtendente> porAtendente) {
        static Resposta de(DesempenhoDaEquipe desempenho) {
            return new Resposta(
                    desempenho.porAtendente().stream().map(PorAtendente::de).toList());
        }
    }
}
