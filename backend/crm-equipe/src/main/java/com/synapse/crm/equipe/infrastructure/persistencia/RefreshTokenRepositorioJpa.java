package com.synapse.crm.equipe.infrastructure.persistencia;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.autenticacao.RefreshTokenRepositorio;

/** Adaptador JPA da porta de refresh tokens. */
@Repository
class RefreshTokenRepositorioJpa implements RefreshTokenRepositorio {

    private final RefreshTokenJpaRepository jpa;
    private final Clock relogio;

    RefreshTokenRepositorioJpa(RefreshTokenJpaRepository jpa, Clock relogio) {
        this.jpa = jpa;
        this.relogio = relogio;
    }

    @Override
    public RefreshTokenArmazenado salvar(
            UUID usuarioId, UUID familia, String tokenHash, Instant expiraEm) {
        RefreshTokenEntity entidade =
                new RefreshTokenEntity(UUID.randomUUID(), usuarioId, tokenHash, familia, expiraEm);
        return jpa.save(entidade).paraDominio();
    }

    @Override
    public Optional<RefreshTokenArmazenado> porHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(RefreshTokenEntity::paraDominio);
    }

    @Override
    public void revogar(UUID id) {
        jpa.revogarPorId(id, relogio.instant());
    }

    @Override
    public void revogarFamilia(UUID familia) {
        jpa.revogarFamilia(familia, relogio.instant());
    }

    @Override
    public void revogarTodosDoUsuario(UUID usuarioId) {
        jpa.revogarPorUsuario(usuarioId, relogio.instant());
    }
}
