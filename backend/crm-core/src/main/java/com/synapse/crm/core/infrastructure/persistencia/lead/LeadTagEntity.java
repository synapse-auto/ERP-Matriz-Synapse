package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Mapeamento da tabela de ligacao {@code lead_tag}.
 *
 * <p>Existe so para o filtro por tag conseguir emitir um {@code EXISTS}. Nao ha {@code @ManyToMany}
 * em {@link LeadEntity} de proposito: uma colecao mapeada convidaria a carregar as tags junto do lead
 * em tela de lista, que e exatamente o gasto que {@code LeadResumo} evita. E o {@code EXISTS} tambem
 * e o que impede a contagem de inflar — um {@code JOIN} devolveria o mesmo lead uma vez por tag
 * casada, e a contagem em tempo real da tela mentiria.
 */
@Entity
@Table(name = "lead_tag")
@IdClass(LeadTagEntity.Chave.class)
class LeadTagEntity {

    @Id
    @Column(name = "lead_id")
    private UUID leadId;

    @Id
    @Column(name = "tag_id")
    private UUID tagId;

    protected LeadTagEntity() {
        // exigido pelo JPA
    }

    /** Nomes de atributo usados pela subconsulta do interpretador. */
    static final class Campos {
        static final String LEAD_ID = "leadId";
        static final String TAG_ID = "tagId";

        private Campos() {}
    }

    /** Chave composta. Publica porque o JPA a instancia por reflexao. */
    public record Chave(UUID leadId, UUID tagId) implements Serializable {
        public Chave() {
            this(null, null);
        }
    }
}
