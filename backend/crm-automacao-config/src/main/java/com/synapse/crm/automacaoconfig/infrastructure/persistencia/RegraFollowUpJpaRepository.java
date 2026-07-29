package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RegraFollowUpJpaRepository extends JpaRepository<RegraFollowUpEntity, UUID> {
    List<RegraFollowUpEntity> findByAtivoTrue();
}
