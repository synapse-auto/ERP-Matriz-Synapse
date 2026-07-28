package com.synapse.crm.core.infrastructure.persistencia.etapa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Pacote-privada: so o adaptador deste pacote fala com ela. Ver ArquiteturaTest. */
interface EtapaJpaRepository extends JpaRepository<EtapaEntity, UUID> {

    List<EtapaEntity> findAllByOrderByOrdemAsc();
}
