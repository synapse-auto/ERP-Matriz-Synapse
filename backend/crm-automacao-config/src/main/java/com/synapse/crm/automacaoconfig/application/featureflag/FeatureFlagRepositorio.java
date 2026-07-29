package com.synapse.crm.automacaoconfig.application.featureflag;

import java.util.List;

import com.synapse.crm.automacaoconfig.domain.featureflag.FeatureFlag;

public interface FeatureFlagRepositorio {
    List<FeatureFlag> listarTodas();
}
