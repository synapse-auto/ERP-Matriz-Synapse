package com.synapse.crm.core.infrastructure.persistencia.tag;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.core.domain.tag.Tag;

/** Mapeamento JPA da tabela {@code tag}. Pacote-privada. */
@Entity
@Table(name = "tag")
class TagEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "cor", nullable = false)
    private String cor;

    @Column(name = "icone")
    private String icone;

    protected TagEntity() {
        // exigido pelo JPA
    }

    TagEntity(Tag tag) {
        this.id = tag.id();
        aplicar(tag);
    }

    void aplicar(Tag tag) {
        this.nome = tag.nome();
        this.cor = tag.cor();
        this.icone = tag.icone();
    }

    Tag paraDominio() {
        return new Tag(id, nome, cor, icone);
    }
}
