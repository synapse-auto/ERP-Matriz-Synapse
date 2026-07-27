package com.synapse.crm.equipe.infrastructure.persistencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Pacote-privado: so o adaptador deste pacote fala com ele. */
interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revogadoEm = :agora"
            + " WHERE t.id = :id AND t.revogadoEm IS NULL")
    void revogarPorId(@Param("id") UUID id, @Param("agora") Instant agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revogadoEm = :agora"
            + " WHERE t.familia = :familia AND t.revogadoEm IS NULL")
    void revogarFamilia(@Param("familia") UUID familia, @Param("agora") Instant agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity t SET t.revogadoEm = :agora"
            + " WHERE t.usuarioId = :usuarioId AND t.revogadoEm IS NULL")
    void revogarPorUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);
}
