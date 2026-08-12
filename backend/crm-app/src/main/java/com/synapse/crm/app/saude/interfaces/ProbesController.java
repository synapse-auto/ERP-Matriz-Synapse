package com.synapse.crm.app.saude.interfaces;

import java.sql.Connection;
import java.util.Map;

import javax.sql.DataSource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Probes públicos estáveis; o diagnóstico detalhado do Actuator fica em /internal-health. */
@RestController
@RequestMapping("/health")
@Tag(name = "Saúde operacional", description = "Probes e diagnóstico do processo.")
class ProbesController {

    private final ApplicationAvailability disponibilidade;
    private final DataSource geral;

    ProbesController(
            ApplicationAvailability disponibilidade,
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource geral) {
        this.disponibilidade = disponibilidade;
        this.geral = geral;
    }

    /** Deliberadamente não toca banco, fila, canal ou qualquer outra dependência. */
    @Operation(
            summary = "Verifica se o processo está vivo",
            description = "Não consulta dependências e, portanto, não causa loop de restart.")
    @ApiResponse(responseCode = "200", description = "Processo vivo.")
    @GetMapping("/liveness")
    ResponseEntity<Map<String, String>> liveness() {
        boolean vivo = disponibilidade.getLivenessState() == LivenessState.CORRECT;
        return resposta(vivo);
    }

    @Operation(
            summary = "Verifica se a instância está pronta",
            description = "Confirma o estado de prontidão e o pool geral do banco.")
    @ApiResponse(responseCode = "200", description = "Instância pronta para tráfego.")
    @ApiResponse(responseCode = "503", description = "Instância temporariamente indisponível.")
    @GetMapping("/readiness")
    ResponseEntity<Map<String, String>> readiness() {
        boolean pronto = disponibilidade.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC
                && bancoGeralAcessivel();
        return resposta(pronto);
    }

    private boolean bancoGeralAcessivel() {
        try (Connection conexao = geral.getConnection()) {
            return conexao.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }

    private static ResponseEntity<Map<String, String>> resposta(boolean positivo) {
        return ResponseEntity.status(positivo ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", positivo ? "UP" : "DOWN"));
    }
}
