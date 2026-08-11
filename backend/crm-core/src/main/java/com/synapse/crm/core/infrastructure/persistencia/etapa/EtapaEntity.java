package com.synapse.crm.core.infrastructure.persistencia.etapa;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.etapa.ResultadoEtapa;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "resultado", nullable = false)
    private ResultadoEtapa resultado;

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
        this.resultado = etapa.resultado();
    }

    EtapaAtendimento paraDominio() {
        return new EtapaAtendimento(id, nome, ordem, corVisual, resultado);
    }
}
