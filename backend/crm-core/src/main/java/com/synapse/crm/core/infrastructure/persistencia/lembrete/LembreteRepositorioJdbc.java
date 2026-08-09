package com.synapse.crm.core.infrastructure.persistencia.lembrete;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lembrete.FiltroLembretes;
import com.synapse.crm.core.application.lembrete.LembreteRepositorio;
import com.synapse.crm.core.application.lembrete.PaginaLembretes;
import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.core.domain.lembrete.StatusLembrete;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

@Repository
class LembreteRepositorioJdbc implements LembreteRepositorio {
    private final JdbcTemplate jdbc;

    LembreteRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PaginaLembretes listar(FiltroLembretes filtro) {
        TransacaoObrigatoria.exigir("listar lembretes");
        StringBuilder sql = new StringBuilder("""
                SELECT l.id, l.lead_id, lead.nome AS lead_nome, l.atendente_id,
                       u.nome AS atendente_nome, l.texto, l.data_hora,
                       l.origem_automatica, l.status::text
                  FROM lembrete l
                  LEFT JOIN lead ON lead.id = l.lead_id
                  JOIN usuario u ON u.id = l.atendente_id
                 WHERE TRUE
                """);
        List<Object> parametros = new ArrayList<>();
        if (filtro.inicio() != null) {
            sql.append(" AND l.data_hora >= ?");
            parametros.add(Timestamp.from(filtro.inicio()));
        }
        if (filtro.fim() != null) {
            sql.append(" AND l.data_hora <= ?");
            parametros.add(Timestamp.from(filtro.fim()));
        }
        if (filtro.status() != null) {
            sql.append(" AND l.status = CAST(? AS status_lembrete)");
            parametros.add(filtro.status().name());
        }
        if (filtro.leadId() != null) {
            sql.append(" AND l.lead_id = ?");
            parametros.add(filtro.leadId());
        }
        sql.append(" ORDER BY l.data_hora, l.id LIMIT ? OFFSET ?");
        parametros.add(filtro.tamanho() + 1);
        parametros.add(Math.multiplyExact(filtro.pagina(), filtro.tamanho()));
        List<Lembrete> itens = jdbc.query(sql.toString(), LembreteRepositorioJdbc::mapear, parametros.toArray());
        boolean temMais = itens.size() > filtro.tamanho();
        if (temMais) itens = new ArrayList<>(itens.subList(0, filtro.tamanho()));
        return new PaginaLembretes(itens, filtro.pagina(), temMais);
    }

    @Override
    public Lembrete criar(UUID leadId, UUID atendenteId, String texto, java.time.Instant dataHora) {
        TransacaoObrigatoria.exigir("criar lembrete");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lembrete (id, lead_id, atendente_id, texto, data_hora, origem_automatica, status)
                VALUES (?, ?, ?, ?, ?, FALSE, 'PENDENTE')
                """, id, leadId, atendenteId, texto, Timestamp.from(dataHora));
        return porId(id).orElseThrow();
    }

    @Override
    public Optional<Lembrete> atualizar(UUID id, String texto, java.time.Instant dataHora, StatusLembrete status) {
        TransacaoObrigatoria.exigir("atualizar lembrete");
        int alterados = jdbc.update("""
                UPDATE lembrete SET texto = ?, data_hora = ?, status = CAST(? AS status_lembrete)
                 WHERE id = ?
                """, texto, Timestamp.from(dataHora), status.name(), id);
        return alterados == 0 ? Optional.empty() : porId(id);
    }

    @Override
    public boolean remover(UUID id) {
        TransacaoObrigatoria.exigir("remover lembrete");
        return jdbc.update("DELETE FROM lembrete WHERE id = ?", id) == 1;
    }

    private Optional<Lembrete> porId(UUID id) {
        return jdbc.query("""
                SELECT l.id, l.lead_id, lead.nome AS lead_nome, l.atendente_id,
                       u.nome AS atendente_nome, l.texto, l.data_hora,
                       l.origem_automatica, l.status::text
                  FROM lembrete l LEFT JOIN lead ON lead.id = l.lead_id
                  JOIN usuario u ON u.id = l.atendente_id WHERE l.id = ?
                """, LembreteRepositorioJdbc::mapear, id).stream().findFirst();
    }

    private static Lembrete mapear(ResultSet linha, int indice) throws SQLException {
        return new Lembrete(
                linha.getObject("id", UUID.class), linha.getObject("lead_id", UUID.class),
                linha.getString("lead_nome"), linha.getObject("atendente_id", UUID.class),
                linha.getString("atendente_nome"), linha.getString("texto"),
                linha.getTimestamp("data_hora").toInstant(), linha.getBoolean("origem_automatica"),
                StatusLembrete.valueOf(linha.getString("status")));
    }
}
