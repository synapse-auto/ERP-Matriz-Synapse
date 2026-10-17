package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * Projeção de autorização, sem dependência do módulo atendimento e sem escrita/JpaRepository.
 * As tabelas da subconsulta continuam sob RLS; não concede acesso a convite pendente.
 * O EXISTS da Specification correlaciona o lead e o usuário, sem trazer listas para a memória.
 */
@Entity
@Immutable
@Subselect("SELECT p.atendimento_id::text || ':' || p.usuario_id::text AS id, a.lead_id, p.usuario_id"
        + " FROM atendimento_participante p JOIN atendimento a ON a.id = p.atendimento_id"
        + " WHERE p.saiu_em IS NULL AND a.status <> 'FINALIZADO'")
@Synchronize({"atendimento", "atendimento_participante"})
class ParticipacaoVisivelLeadEntity {
    @Id private String id;
    private UUID leadId;
    private UUID usuarioId;
}
