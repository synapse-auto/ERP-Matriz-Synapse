package com.synapse.crm.automacaoconfig.domain.featureflag;

/** Uma feature flag ({@code feature_flag}, V9) — habilita modulo por filho sem deploy. */
public record FeatureFlag(String chave, boolean habilitado, String descricao) {}
