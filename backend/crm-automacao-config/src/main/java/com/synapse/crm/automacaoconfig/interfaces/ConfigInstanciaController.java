package com.synapse.crm.automacaoconfig.interfaces;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.automacaoconfig.application.featureflag.FeatureService;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;

/**
 * Configuracao da instancia para o frontend (E07 §4) — a fundacao de frontend que a E10 depende
 * existir: feature flags, tema e textos.
 */
@RestController
@RequestMapping("/api/v1/config")
class ConfigInstanciaController {

    private final FeatureService features;
    private final ConfiguracaoDeInstanciaResources recursos;

    ConfigInstanciaController(FeatureService features, ConfiguracaoDeInstanciaResources recursos) {
        this.features = features;
        this.recursos = recursos;
    }

    @GetMapping("/features")
    List<String> features() {
        return features.habilitadas();
    }

    @GetMapping("/tema")
    JsonNode tema() {
        return recursos.tema();
    }

    @GetMapping("/textos")
    JsonNode textos() {
        return recursos.textos();
    }
}
