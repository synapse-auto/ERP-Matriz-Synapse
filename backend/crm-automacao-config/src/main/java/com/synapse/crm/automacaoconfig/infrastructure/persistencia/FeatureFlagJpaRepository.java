package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

interface FeatureFlagJpaRepository extends JpaRepository<FeatureFlagEntity, String> {}
