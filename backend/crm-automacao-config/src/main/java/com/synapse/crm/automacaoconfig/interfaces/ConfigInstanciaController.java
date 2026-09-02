package com.synapse.crm.automacaoconfig.interfaces;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.automacaoconfig.application.featureflag.FeatureService;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;

/**
 * Configuracao da instancia para o frontend (E07 §4) — a fundacao de frontend que a E10 depende
 * existir: feature flags, tema e textos.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Configuração da instância", description = "Feature flags, tema e catálogo de textos consumidos pelo frontend.")
class ConfigInstanciaController {

    private final FeatureService features;
    private final ConfiguracaoDeInstanciaResources recursos;
    private final CanalGateway canal;

    ConfigInstanciaController(
            FeatureService features, ConfiguracaoDeInstanciaResources recursos, CanalGateway canal) {
        this.features = features;
        this.recursos = recursos;
        this.canal = canal;
    }

    @Operation(
            summary = "Listar features habilitadas",
            description = "Retorna somente os nomes das capacidades habilitadas para a instância.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Nomes das features habilitadas."))
    @GetMapping("/features")
    List<String> features() {
        return features.habilitadas();
    }

    @Operation(
            summary = "Obter capacidades do canal",
            description = "Retorna as capacidades do canal ativo que orientam a composição da primeira mensagem.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = @ApiResponse(responseCode = "200", description = "Capacidades do canal ativo."))
    @GetMapping("/canal")
    CapacidadeDoCanal canal() {
        return new CapacidadeDoCanal(canal.exigeTemplateForaDaJanela());
    }

    @Operation(
            summary = "Obter tema",
            description = "Retorna os design tokens necessários inclusive para a tela de login; não contém credenciais.",
            responses = @ApiResponse(responseCode = "200", description = "Documento JSON do tema."))
    @GetMapping("/tema")
    JsonNode tema() {
        return recursos.tema();
    }

    @Operation(
            summary = "Obter catálogo de textos",
            description = "Retorna os textos configuráveis necessários inclusive antes da autenticação.",
            responses = @ApiResponse(responseCode = "200", description = "Documento JSON de textos."))
    @GetMapping("/textos")
    JsonNode textos() {
        return recursos.textos();
    }

    @Operation(
            summary = "Obter a marca da instância",
            description = "Retorna logo.png quando o filho tem marca própria configurada; 404 quando não tem — caso normal, o frontend cai no fallback.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Imagem da marca."),
                @ApiResponse(responseCode = "404", description = "Instância sem logo configurado.")
            })
    @GetMapping("/logo")
    ResponseEntity<byte[]> logo() {
        byte[] logo = recursos.logo();
        if (logo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(logo);
    }

    record CapacidadeDoCanal(boolean exigeTemplateForaDaJanela) {}
}
