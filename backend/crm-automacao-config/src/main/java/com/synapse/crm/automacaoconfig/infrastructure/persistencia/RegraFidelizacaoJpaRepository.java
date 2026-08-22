package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RegraFidelizacaoJpaRepository extends JpaRepository<RegraFidelizacaoEntity, UUID> {
    List<RegraFidelizacaoEntity> findByAtivoTrueOrderByDiasSemContatoAsc();
    List<RegraFidelizacaoEntity> findAllByOrderByDiasSemContatoAsc();
}
