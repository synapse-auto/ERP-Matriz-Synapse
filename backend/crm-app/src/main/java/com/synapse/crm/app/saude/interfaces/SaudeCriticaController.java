package com.synapse.crm.app.saude.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.app.saude.application.EstadoDaSaude;
import com.synapse.crm.app.saude.application.ResultadoDaSaudeCritica;
import com.synapse.crm.app.saude.application.VerificarSaudeCriticaUseCase;

/** Alvo detalhado do watchdog externo; nunca usado como liveness do container. */
@RestController
@RequestMapping("/health")
@Tag(name = "Saúde operacional", description = "Diagnóstico do caminho crítico de mensagens.")
public class SaudeCriticaController {

    private final VerificarSaudeCriticaUseCase verificar;

    public SaudeCriticaController(VerificarSaudeCriticaUseCase verificar) {
        this.verificar = verificar;
    }

    @Operation(
            summary = "Verifica o caminho crítico de mensagens",
            description =
                    "Distingue falhas críticas de degradação e identifica cada componente afetado. Não substitui o liveness.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Caminho saudável ou apenas degradado."),
        @ApiResponse(responseCode = "503", description = "Ao menos um componente crítico falhou.")
    })
    @GetMapping("/critical")
    ResponseEntity<ResultadoDaSaudeCritica> verificar() {
        ResultadoDaSaudeCritica resultado = verificar.executar();
        HttpStatus status = resultado.status() == EstadoDaSaude.DOWN
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(resultado);
    }
}
