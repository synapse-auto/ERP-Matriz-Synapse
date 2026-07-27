package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * Mapeamento JPA da tabela {@code lead}.
 *
 * <p>Deliberadamente fora do dominio: o dominio nao conhece {@code jakarta.persistence}, e o teste
 * de arquitetura reprova se conhecer. Esta classe e pacote-privada em intencao — so o adaptador
 * deste pacote deveria toca-la.
 */
@Entity
@Table(name = "lead")
class LeadEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status_basico", nullable = false)
    private StatusBasicoLead statusBasico;

    @Column(name = "atendente_responsavel_id")
    private UUID atendenteResponsavelId;

    protected LeadEntity() {
        // exigido pelo JPA
    }

    Lead paraDominio() {
        return new Lead(id, nome, statusBasico, atendenteResponsavelId);
    }

    /** Nomes de coluna usados pela Specification. Centralizados para nao virarem string solta. */
    static final class Campos {
        static final String STATUS_BASICO = "statusBasico";
        static final String ATENDENTE_RESPONSAVEL_ID = "atendenteResponsavelId";
        static final String NOME = "nome";

        private Campos() {}
    }
}
