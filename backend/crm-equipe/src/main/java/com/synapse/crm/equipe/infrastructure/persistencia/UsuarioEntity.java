package com.synapse.crm.equipe.infrastructure.persistencia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Mapeamento JPA da tabela {@code usuario}. */
@Entity
@Table(name = "usuario")
class UsuarioEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "papel", nullable = false)
    private PapelUsuario papel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status_presenca", nullable = false)
    private StatusPresenca statusPresenca;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "telefone")
    private String telefone;

    @Column(name = "cargo")
    private String cargo;

    @Column(name = "foto_referencia")
    private String fotoReferencia;

    /** {@code null} = senha provisoria, nunca trocada pelo dono (E29). */
    @Column(name = "senha_alterada_em")
    private Instant senhaAlteradaEm;

    protected UsuarioEntity() {
        // exigido pelo JPA
    }

    Usuario paraDominio() {
        return new Usuario(id, nome, email, senhaHash, papel, statusPresenca, ativo, false, telefone, cargo,
                fotoReferencia, senhaAlteradaEm);
    }
}
