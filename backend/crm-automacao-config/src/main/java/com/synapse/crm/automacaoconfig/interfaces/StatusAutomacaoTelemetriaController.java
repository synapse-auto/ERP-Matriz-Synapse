package com.synapse.crm.automacaoconfig.interfaces;

import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.automacaoconfig.application.telemetria.ObterStatusAutomacaoTelemetriaUseCase;
import com.synapse.crm.automacaoconfig.domain.telemetria.StatusAutomacaoTelemetria;

/**
 * Os quatro cards do topo da tela de Automação (E17b §Bloco 6): mensagens enviadas, clientes
 * transferidos, conexão da Automação e status do CRM.
 */
@RestController
@RequestMapping("/api/v1/automacao/telemetria")
@Tag(name = "Telemetria da automação", description = "Snapshot operacional consumido pelos cards do topo da tela de Automação.")
@SecurityRequirement(name = "bearerAuth")
class StatusAutomacaoTelemetriaController {

    private final ObterStatusAutomacaoTelemetriaUseCase obter;

    StatusAutomacaoTelemetriaController(ObterStatusAutomacaoTelemetriaUseCase obter) {
        this.obter = obter;
    }

    @Operation(
            summary = "Obter telemetria da automação",
            description = "Retorna o snapshot mais recente de status_automacao_telemetria.",
            responses = @ApiResponse(responseCode = "200", description = "Snapshot de telemetria."))
    @GetMapping
    StatusAutomacaoTelemetriaResposta obter() {
        return StatusAutomacaoTelemetriaResposta.de(obter.executar());
    }

    record StatusAutomacaoTelemetriaResposta(
            long mensagensEnviadas,
            long clientesTransferidos,
            boolean conexaoAutomacaoAtiva,
            boolean crmOnline,
            Instant atualizadoEm) {

        static StatusAutomacaoTelemetriaResposta de(StatusAutomacaoTelemetria status) {
            return new StatusAutomacaoTelemetriaResposta(
                    status.mensagensEnviadas(),
                    status.clientesTransferidos(),
                    status.conexaoAutomacaoAtiva(),
                    status.crmOnline(),
                    status.atualizadoEm());
        }
    }
}
