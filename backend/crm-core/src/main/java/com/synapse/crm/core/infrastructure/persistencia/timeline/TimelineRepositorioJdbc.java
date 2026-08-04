package com.synapse.crm.core.infrastructure.persistencia.timeline;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.timeline.PaginaTimeline;
import com.synapse.crm.core.application.timeline.TimelineRepositorio;
import com.synapse.crm.core.domain.timeline.EventoTimeline;
import com.synapse.crm.core.domain.timeline.OrigemEvento;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

/** Leitura paginada da timeline, sem {@code COUNT(*)} sobre uma tabela que cresce sem limite. */
@Repository
class TimelineRepositorioJdbc implements TimelineRepositorio {

    private static final String SQL =
            """
            SELECT id, lead_id, atendimento_id, tipo, descricao, origem, criado_em
              FROM evento_timeline
             WHERE lead_id = ?
             ORDER BY criado_em DESC, id DESC
             LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate jdbc;

    TimelineRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PaginaTimeline listar(UUID leadId, int pagina, int tamanho) {
        TransacaoObrigatoria.exigir("listar timeline do lead");
        long deslocamento = Math.multiplyExact((long) pagina, tamanho);
        List<EventoTimeline> encontrados =
                jdbc.query(SQL, TimelineRepositorioJdbc::mapear, leadId, tamanho + 1, deslocamento);
        boolean temMais = encontrados.size() > tamanho;
        List<EventoTimeline> eventos = temMais
                ? new ArrayList<>(encontrados.subList(0, tamanho))
                : encontrados;
        return new PaginaTimeline(eventos, pagina, temMais);
    }

    private static EventoTimeline mapear(ResultSet linha, int indice) throws SQLException {
        return new EventoTimeline(
                linha.getObject("id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                linha.getObject("atendimento_id", UUID.class),
                linha.getString("tipo"),
                linha.getString("descricao"),
                OrigemEvento.valueOf(linha.getString("origem")),
                linha.getTimestamp("criado_em").toInstant());
    }
}
