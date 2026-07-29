package com.synapse.crm.core.infrastructure.persistencia.campocustomizado;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;

/**
 * Mapeamento JPA de {@code campo_customizado}.
 *
 * <p>{@code tipo} e {@code VARCHAR} com {@code CHECK} no banco (V18), nao um ENUM nativo do Postgres
 * — por isso {@code @Enumerated(STRING)} simples, sem {@code @JdbcTypeCode(NAMED_ENUM)} como em
 * {@code LeadEntity.statusBasico}.
 */
@Entity
@Table(name = "campo_customizado")
class CampoCustomizadoEntity {

    @Id
    @Column(name = "chave")
    private String chave;

    @Column(name = "rotulo", nullable = false)
    private String rotulo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoCampoCustomizado tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opcoes")
    private List<String> opcoes;

    @Column(name = "obrigatorio", nullable = false)
    private boolean obrigatorio;

    @Column(name = "filtravel", nullable = false)
    private boolean filtravel;

    @Column(name = "ordem", nullable = false)
    private short ordem;

    protected CampoCustomizadoEntity() {
        // exigido pelo JPA
    }

    CampoCustomizado paraDominio() {
        return new CampoCustomizado(
                chave, rotulo, tipo, opcoes == null ? List.of() : opcoes, obrigatorio, filtravel, ordem);
    }
}
