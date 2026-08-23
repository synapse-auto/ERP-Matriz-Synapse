package com.synapse.crm.equipe.interfaces.internal;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.disponibilidade.ListarAtendentesDisponiveisUseCase;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** {@code /internal/v1/atendentes/disponiveis} — parte do contrato da Automacao (E07 §1). */
@RestController
@RequestMapping("/internal/v1/atendentes")
@Tag(name = "Disponibilidade interna", description = "Atendentes elegíveis para distribuição pela automação.")
@SecurityRequirement(name = "synapseToken")
class AtendentesDisponiveisInternalController {

    private final ListarAtendentesDisponiveisUseCase listar;

    AtendentesDisponiveisInternalController(ListarAtendentesDisponiveisUseCase listar) {
        this.listar = listar;
    }

    @Operation(
            summary = "Listar atendentes disponíveis",
            description = "Retorna os atendentes ativos e disponíveis na ordem recomendada de distribuição: menor carga, maior tempo desde o último recebimento e id.",
            responses = @ApiResponse(responseCode = "200", description = "Atendentes disponíveis."))
    @GetMapping("/disponiveis")
    List<AtendenteResposta> disponiveis() {
        return ContextoDeServico.buscarComo(
                        "listar-atendentes-disponiveis",
                        () -> listar.executar().stream().map(AtendenteResposta::de).toList());
    }

    record AtendenteResposta(UUID usuarioId, String nome, String email) {
        static AtendenteResposta de(AtendenteDisponivelParaIa atendente) {
            return new AtendenteResposta(atendente.usuarioId(), atendente.nome(), atendente.email());
        }
    }
}
