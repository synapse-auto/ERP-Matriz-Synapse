package com.synapse.crm.atendimento.interfaces;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.AtendenteParaTransferenciaRepositorio.Destino;
import com.synapse.crm.atendimento.application.ListarDestinosDeTransferenciaUseCase;

/**
 * {@code GET /api/v1/atendimentos/destinos-de-transferencia} — id e nome para o diálogo de
 * transferência. Não substitui {@code GET /api/v1/usuarios}.
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
            description = "Retorna identificador e nome dos atendentes ativos. Sem e-mail, papel, presença ou métricas.",
            responses = @ApiResponse(responseCode = "200", description = "Atendentes que podem receber a conversa."))
    @GetMapping("/destinos-de-transferencia")
    List<DestinoResposta> listar() {
        return listar.executar().stream().map(DestinoResposta::de).toList();
    }

    record DestinoResposta(UUID id, String nome) {
        static DestinoResposta de(Destino destino) {
            return new DestinoResposta(destino.id(), destino.nome());
        }
    }
}
