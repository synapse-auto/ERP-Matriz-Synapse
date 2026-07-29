package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.automacaoconfig.domain.featureflag.FeatureFlag;

/** Mapeamento JPA de {@code feature_flag} (V9). */
@Entity
@Table(name = "feature_flag")
class FeatureFlagEntity {

    @Id
    @Column(name = "chave")
    private String chave;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado;

    @Column(name = "descricao")
    private String descricao;

    protected FeatureFlagEntity() {
        // exigido pelo JPA
    }

    FeatureFlag paraDominio() {
        return new FeatureFlag(chave, habilitado, descricao);
    }
}
