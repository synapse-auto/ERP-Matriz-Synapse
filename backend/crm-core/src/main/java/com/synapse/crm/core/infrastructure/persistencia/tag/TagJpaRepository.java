package com.synapse.crm.core.infrastructure.persistencia.tag;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Pacote-privada: so o adaptador deste pacote fala com ela. Ver ArquiteturaTest. */
interface TagJpaRepository extends JpaRepository<TagEntity, UUID> {

    List<TagEntity> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
}
