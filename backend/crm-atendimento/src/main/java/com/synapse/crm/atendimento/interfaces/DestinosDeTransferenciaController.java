package com.synapse.crm.atendimento.interfaces;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.AtendenteParaTransferenciaRepositorio.Destino;
import com.synapse.crm.atendimento.application.ListarDestinosDeTransferenciaUseCase;

/**
 * {@code GET /api/v1/atendimentos/destinos-de-transferencia} — id, nome e papel opcional para o
 * diálogo de transferência. Não substitui {@code GET /api/v1/usuarios}.
 */
@RestController
@RequestMapping("/api/v1/atendimentos")
@Tag(name = "Ações de atendimento", description = "Envio, transferência e finalização de conversas visíveis.")
@SecurityRequirement(name = "bearerAuth")
class DestinosDeTransferenciaController {

    private final ListarDestinosDeTransferenciaUseCase listar;

    DestinosDeTransferenciaController(ListarDestinosDeTransferenciaUseCase listar) {
        this.listar = listar;
    }

    @Operation(
            summary = "Listar destinos de transferência",
            description = "Retorna identificador, nome e papel dos atendentes ativos e dos subgestores online disponíveis para a IA. O papel pode ser omitido por implementações legadas; não inclui e-mail, presença ou métricas.",
            responses = @ApiResponse(responseCode = "200", description = "Atendentes que podem receber a conversa."))
    @GetMapping("/destinos-de-transferencia")
    List<DestinoResposta> listar() {
        return listar.executar().stream().map(DestinoResposta::de).toList();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DestinoResposta(
            UUID id,
            String nome,
            @Schema(description = "Papel elegível do destino. Ausente em respostas legadas.", nullable = true,
                    allowableValues = {"ATENDENTE", "SUBGESTOR"})
            String papel) {
        static DestinoResposta de(Destino destino) {
            return new DestinoResposta(
                    destino.id(), destino.nome(), destino.papel() == null ? null : destino.papel().name());
        }
    }
}
