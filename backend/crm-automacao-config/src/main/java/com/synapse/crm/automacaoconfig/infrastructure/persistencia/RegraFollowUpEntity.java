package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFollowUp;

/** Mapeamento JPA de {@code regra_follow_up} (V7). */
@Entity
@Table(name = "regra_follow_up")
class RegraFollowUpEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "tempo_minutos", nullable = false)
    private int tempoMinutos;

    @Column(name = "texto", nullable = false)
    private String texto;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    protected RegraFollowUpEntity() {
        // exigido pelo JPA
    }

    RegraFollowUpEntity(RegraFollowUp regra) {
        this.id = regra.id(); this.nome = regra.nome(); this.tempoMinutos = regra.tempoMinutos();
        this.texto = regra.texto(); this.ativo = regra.ativo();
    }

    void atualizar(RegraFollowUp regra) {
        this.nome = regra.nome(); this.tempoMinutos = regra.tempoMinutos(); this.texto = regra.texto(); this.ativo = regra.ativo();
    }

    RegraFollowUp paraDominio() {
        return new RegraFollowUp(id, nome, tempoMinutos, texto, ativo);
    }
}
