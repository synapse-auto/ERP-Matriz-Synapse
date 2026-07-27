package com.synapse.crm.equipe.infrastructure.persistencia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.equipe.application.autenticacao.RefreshTokenRepositorio.RefreshTokenArmazenado;

/** Mapeamento JPA da tabela {@code refresh_token}. Guarda o hash, nunca o token. */
@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "familia", nullable = false)
    private UUID familia;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    protected RefreshTokenEntity() {
        // exigido pelo JPA
    }

    RefreshTokenEntity(UUID id, UUID usuarioId, String tokenHash, UUID familia, Instant expiraEm) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.familia = familia;
        this.expiraEm = expiraEm;
    }

    RefreshTokenArmazenado paraDominio() {
        return new RefreshTokenArmazenado(id, usuarioId, familia, expiraEm, revogadoEm);
    }
}
