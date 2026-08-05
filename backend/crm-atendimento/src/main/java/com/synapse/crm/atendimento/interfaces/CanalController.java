package com.synapse.crm.atendimento.interfaces;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.canal.CanalResumo;
import com.synapse.crm.atendimento.application.canal.ListarCanaisUseCase;

/** Metadados de canal usados para traduzir a origem da ficha; nunca devolve credenciais. */
@RestController
@RequestMapping("/api/v1/canais")
@Tag(name = "Canais", description = "Metadados não sensíveis dos canais de atendimento.")
@SecurityRequirement(name = "bearerAuth")
class CanalController {

    private final ListarCanaisUseCase listar;

    CanalController(ListarCanaisUseCase listar) {
        this.listar = listar;
    }

    @Operation(
            summary = "Listar canais",
            description = "Retorna identificador, nome, tipo e estado dos canais sem credenciais nem referências de segredo.",
            responses = @ApiResponse(responseCode = "200", description = "Canais configurados."))
    @GetMapping
    List<CanalResumo> listar() {
        return listar.executar();
    }
}
