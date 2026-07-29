package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.featureflag.FeatureFlagRepositorio;
import com.synapse.crm.automacaoconfig.domain.featureflag.FeatureFlag;

/**
 * Sem cache Redis aqui, ao contrario de {@code configuracao_automacao}: nesta etapa nao ha
 * {@code PUT} de feature flag (fase 2, tela de gestao), entao nao ha invalidacao para acertar — cache
 * sem forma de invalidar so trocaria "banco lento" por "banco errado".
 */
@Repository
class FeatureFlagRepositorioJpa implements FeatureFlagRepositorio {

    private final FeatureFlagJpaRepository jpa;

    FeatureFlagRepositorioJpa(FeatureFlagJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<FeatureFlag> listarTodas() {
        return jpa.findAll().stream().map(FeatureFlagEntity::paraDominio).toList();
    }
}
