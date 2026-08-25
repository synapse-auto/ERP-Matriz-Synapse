package com.synapse.crm.core.infrastructure.persistencia.lembrete;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lembrete.LembreteDaAutomacaoRepositorio;
import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.core.domain.lembrete.StatusLembrete;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Mantem reserva da V32 e lembrete na mesma transacao fisica do pool de chat. */
@Repository
class LembreteDaAutomacaoRepositorioJdbc implements LembreteDaAutomacaoRepositorio {

    private final JdbcTemplate chat;

    LembreteDaAutomacaoRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Lembrete criar(UUID leadId, UUID atendenteId, String texto, Instant dataHora) {
        TransacaoObrigatoria.exigir("criar lembrete da Automacao");
        UUID id = UUID.randomUUID();
        chat.update(
                """
                INSERT INTO lembrete
                       (id, lead_id, atendente_id, texto, data_hora, origem_automatica, status)
                VALUES (?, ?, ?, ?, ?, TRUE, 'PENDENTE')
                """,
                id,
                leadId,
                atendenteId,
                texto,
                Timestamp.from(dataHora));
        return chat.queryForObject(
                """
                SELECT l.id, l.lead_id, lead.nome AS lead_nome, l.atendente_id,
                       u.nome AS atendente_nome, l.texto, l.data_hora,
                       l.origem_automatica, l.status::text
                  FROM lembrete l
                  LEFT JOIN lead ON lead.id = l.lead_id
                  JOIN usuario u ON u.id = l.atendente_id
                 WHERE l.id = ?
                """,
                LembreteDaAutomacaoRepositorioJdbc::mapear,
                id);
    }

    private static Lembrete mapear(ResultSet linha, int indice) throws SQLException {
        return new Lembrete(
                linha.getObject("id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                linha.getString("lead_nome"),
                linha.getObject("atendente_id", UUID.class),
                linha.getString("atendente_nome"),
                linha.getString("texto"),
                linha.getTimestamp("data_hora").toInstant(),
                linha.getBoolean("origem_automatica"),
                StatusLembrete.valueOf(linha.getString("status")));
    }
}
