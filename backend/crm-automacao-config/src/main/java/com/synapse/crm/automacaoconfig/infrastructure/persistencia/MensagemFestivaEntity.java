package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;

/** Mapeamento JPA de {@code mensagem_festiva} (V7/V74). */
@Entity
@Table(name = "mensagem_festiva")
class MensagemFestivaEntity {
    @Id
    private UUID id;

    @Column(name = "titulo", nullable = false, length = 100)
    private String titulo;

    @Column(name = "icone", nullable = false, length = 80)
    private String icone;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "texto", nullable = false)
    private String mensagem;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    protected MensagemFestivaEntity() {}

    MensagemFestivaEntity(MensagemFestiva mensagem) {
        atualizar(mensagem);
        this.id = mensagem.id();
    }

    void atualizar(MensagemFestiva mensagem) {
        this.titulo = mensagem.titulo();
        this.icone = mensagem.icone();
        this.data = mensagem.data();
        this.mensagem = mensagem.mensagem();
        this.ativo = mensagem.ativo();
    }

    MensagemFestiva paraDominio() {
        return new MensagemFestiva(id, titulo, icone, data, mensagem, ativo);
    }
}
