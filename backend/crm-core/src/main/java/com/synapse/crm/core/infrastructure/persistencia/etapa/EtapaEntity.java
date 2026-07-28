package com.synapse.crm.core.infrastructure.persistencia.etapa;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.synapse.crm.core.domain.etapa.EtapaAtendimento;

/** Mapeamento JPA da tabela {@code etapa_atendimento}. Pacote-privada. */
@Entity
@Table(name = "etapa_atendimento")
class EtapaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "ordem", nullable = false)
    private short ordem;

    @Column(name = "cor_visual")
    private String corVisual;

    protected EtapaEntity() {
        // exigido pelo JPA
    }

    EtapaEntity(EtapaAtendimento etapa) {
        this.id = etapa.id();
        aplicar(etapa);
    }

    void aplicar(EtapaAtendimento etapa) {
        this.nome = etapa.nome();
        this.ordem = etapa.ordem();
        this.corVisual = etapa.corVisual();
    }

    EtapaAtendimento paraDominio() {
        return new EtapaAtendimento(id, nome, ordem, corVisual);
    }
}
