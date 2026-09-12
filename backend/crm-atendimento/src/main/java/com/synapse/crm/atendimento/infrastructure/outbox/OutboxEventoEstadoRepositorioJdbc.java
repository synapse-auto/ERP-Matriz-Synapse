package com.synapse.crm.atendimento.infrastructure.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Persistência durável do sinal de reconciliação, na mesma outbox e transação do atendimento. */
@Repository
class OutboxEventoEstadoRepositorioJdbc {

    static final String TIPO = "tempo-real.atendimento.estado.v1";
    private static final int LIMITE_ERRO = 500;
    private final JdbcTemplate chat;
    private final ObjectMapper json;

    OutboxEventoEstadoRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource dataSource, ObjectMapper json) {
        this.chat = new JdbcTemplate(dataSource);
        this.json = json;
    }

    void enfileirar(EventoCanonicoDeAtendimento evento) {
        TransacaoObrigatoria.exigir("enfileirarEventoEstadoAtendimento");
        chat.update(
                "INSERT INTO outbox_evento (id, tipo, payload, criado_em, proxima_tentativa_em)"
                        + " VALUES (?, ?, ?::jsonb, ?, ?)",
                evento.eventoId(),
                TIPO,
                serializar(evento),
                Timestamp.from(evento.ocorridoEm()),
                Timestamp.from(evento.ocorridoEm()));
    }

    List<Pendente> reservar(int limite, Instant agora, Instant reservaAte) {
        TransacaoObrigatoria.exigir("reservarEventosEstadoAtendimento");
        return chat.query(
                """
                WITH candidatos AS (
                    SELECT id FROM outbox_evento
                     WHERE tipo = ? AND publicado_em IS NULL AND esgotado_em IS NULL
                       AND proxima_tentativa_em <= ?
                     ORDER BY proxima_tentativa_em, id
                     LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_evento o SET proxima_tentativa_em = ?
                  FROM candidatos c WHERE o.id = c.id
                RETURNING o.id, o.payload, o.tentativas
                """,
                this::lerPendente,
                TIPO,
                Timestamp.from(agora),
                limite,
                Timestamp.from(reservaAte));
    }

    boolean marcarPublicado(UUID id, Instant quando) {
        TransacaoObrigatoria.exigir("publicarEventoEstadoAtendimento");
        return chat.update(
                "UPDATE outbox_evento SET publicado_em = ?, ultimo_erro = NULL"
                        + " WHERE id = ? AND publicado_em IS NULL AND esgotado_em IS NULL",
                Timestamp.from(quando), id) == 1;
    }

    boolean reagendar(UUID id, Instant proxima, String erro) {
        TransacaoObrigatoria.exigir("reagendarEventoEstadoAtendimento");
        return chat.update(
                "UPDATE outbox_evento SET tentativas = tentativas + 1, proxima_tentativa_em = ?,"
                        + " ultimo_erro = ? WHERE id = ? AND publicado_em IS NULL AND esgotado_em IS NULL",
                Timestamp.from(proxima), truncar(erro), id) == 1;
    }

    boolean esgotar(UUID id, Instant quando, String erro) {
        TransacaoObrigatoria.exigir("esgotarEventoEstadoAtendimento");
        return chat.update(
                "UPDATE outbox_evento SET tentativas = tentativas + 1, esgotado_em = ?, ultimo_erro = ?"
                        + " WHERE id = ? AND publicado_em IS NULL AND esgotado_em IS NULL",
                Timestamp.from(quando), truncar(erro), id) == 1;
    }

    private String serializar(EventoCanonicoDeAtendimento evento) {
        ObjectNode payload = json.createObjectNode();
        payload.put("eventoId", evento.eventoId().toString());
        payload.put("versaoContrato", evento.versaoContrato());
        payload.put("eventoTipo", evento.tipo().name());
        payload.put("atendimentoId", evento.atendimentoId().toString());
        payload.put("leadId", evento.leadId().toString());
        payload.put("versao", evento.versao());
        payload.put("ocorridoEm", evento.ocorridoEm().toString());
        return payload.toString();
    }

    private Pendente lerPendente(ResultSet linha, int indice) throws SQLException {
        JsonNode payload;
        try {
            payload = json.readTree(linha.getString("payload"));
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("payload do evento de estado ilegivel", erro);
        }
        EventoCanonicoDeAtendimento evento = new EventoCanonicoDeAtendimento(
                UUID.fromString(payload.path("eventoId").asText()),
                payload.path("versaoContrato").asInt(),
                EventoCanonicoDeAtendimento.Tipo.valueOf(payload.path("eventoTipo").asText()),
                UUID.fromString(payload.path("atendimentoId").asText()),
                UUID.fromString(payload.path("leadId").asText()),
                payload.path("versao").asLong(),
                Instant.parse(payload.path("ocorridoEm").asText()));
        return new Pendente(linha.getObject("id", UUID.class), evento, linha.getInt("tentativas"));
    }

    private static String truncar(String erro) {
        if (erro == null || erro.length() <= LIMITE_ERRO) return erro;
        return erro.substring(0, LIMITE_ERRO) + "...";
    }

    record Pendente(UUID outboxId, EventoCanonicoDeAtendimento evento, int tentativas) {}
}
