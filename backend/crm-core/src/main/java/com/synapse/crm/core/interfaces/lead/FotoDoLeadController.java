package com.synapse.crm.core.interfaces.lead;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.core.application.lead.foto.ObterFotoDoLeadUseCase;

/**
 * Entrega da foto do lead, espelhando {@code GET /api/v1/me/foto/{id}} (E50).
 *
 * <p>Autenticada e sem cache. O browser nunca recebe URL do storage, e a RLS/RN-CRM-01 continua
 * valendo: um atendente que nao enxerga o lead recebe 404 aqui, o mesmo 404 de "nao existe".
 * {@code ContextoDeServico} nao entra — esta e requisicao de usuario.
 */
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Foto do lead", description = "Entrega autenticada da foto de perfil do lead.")
@SecurityRequirement(name = "bearerAuth")
class FotoDoLeadController {

    private final ObterFotoDoLeadUseCase foto;

    FotoDoLeadController(ObterFotoDoLeadUseCase foto) {
        this.foto = foto;
    }

    @Operation(
            summary = "Entregar foto do lead",
            description = "Entrega a foto processada pelo backend para os avatares autenticados.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Imagem da foto do lead."),
                @ApiResponse(responseCode = "404", description = "Lead sem foto, inexistente ou não visível.")
            })
    @GetMapping("/{id}/foto")
    ResponseEntity<byte[]> foto(
            @Parameter(description = "Identificador do lead.", required = true) @PathVariable UUID id) {
        return foto.executar(id)
                .map(arquivo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(arquivo.mimetype()))
                        .cacheControl(CacheControl.noCache())
                        .body(arquivo.conteudo()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
