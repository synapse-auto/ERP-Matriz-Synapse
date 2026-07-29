package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFidelizacao;

/** Mapeamento JPA de {@code regra_fidelizacao} (V7). Somente leitura nesta etapa. */
@Entity
@Table(name = "regra_fidelizacao")
class RegraFidelizacaoEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "dias_sem_contato", nullable = false)
    private int diasSemContato;

    @Column(name = "mensagem", nullable = false)
    private String mensagem;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    protected RegraFidelizacaoEntity() {
        // exigido pelo JPA
    }

    RegraFidelizacao paraDominio() {
        return new RegraFidelizacao(id, diasSemContato, mensagem, ativo);
    }
}
