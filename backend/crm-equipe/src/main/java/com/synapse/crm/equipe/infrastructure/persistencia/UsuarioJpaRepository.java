package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Pacote-privado: so o adaptador deste pacote fala com ele. */
interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, UUID> {

    Optional<UsuarioEntity> findByEmailIgnoreCase(String email);
}
