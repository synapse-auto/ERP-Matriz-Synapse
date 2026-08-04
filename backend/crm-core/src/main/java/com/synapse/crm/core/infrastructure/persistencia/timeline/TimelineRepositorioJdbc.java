package com.synapse.crm.core.infrastructure.persistencia.timeline;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.timeline.PaginaTimeline;
import com.synapse.crm.core.application.timeline.TimelineRepositorio;
import com.synapse.crm.core.domain.timeline.EventoTimeline;
import com.synapse.crm.core.domain.timeline.OrigemEvento;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

/** Leitura paginada que renderiza os nomes atuais e preserva o snapshot legado como fallback. */
@Repository
class TimelineRepositorioJdbc implements TimelineRepositorio {

    private static final String SQL =
            """
            SELECT evento.id, evento.lead_id, evento.atendimento_id, evento.tipo,
                   evento.descricao, evento.origem, evento.ator_id,
                   evento.dados::text AS dados_json, evento.criado_em,
                   ator.nome AS ator_nome,
                   anterior.nome AS dono_anterior_nome,
                   origem.nome AS de_atendente_nome,
                   destino.nome AS para_atendente_nome,
                   (evento.dados->>'tinhaDonoAnterior')::boolean AS tinha_dono_anterior,
                   (evento.dados->>'deIa')::boolean AS de_ia,
                   (evento.dados->>'paraIa')::boolean AS para_ia
              FROM evento_timeline evento
              LEFT JOIN usuario ator ON ator.id = evento.ator_id
              LEFT JOIN usuario anterior
                     ON anterior.id = NULLIF(evento.dados->>'donoAnteriorId', '')::uuid
              LEFT JOIN usuario origem
                     ON origem.id = NULLIF(evento.dados->>'deAtendenteId', '')::uuid
              LEFT JOIN usuario destino
                     ON destino.id = NULLIF(evento.dados->>'paraAtendenteId', '')::uuid
             WHERE evento.lead_id = ?
             ORDER BY evento.criado_em DESC, evento.id DESC
             LIMIT ? OFFSET ?
            """;

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    TimelineRepositorioJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public PaginaTimeline listar(UUID leadId, int pagina, int tamanho) {
        TransacaoObrigatoria.exigir("listar timeline do lead");
        long deslocamento = Math.multiplyExact((long) pagina, tamanho);
        List<EventoTimeline> encontrados =
                jdbc.query(SQL, this::mapear, leadId, tamanho + 1, deslocamento);
        boolean temMais = encontrados.size() > tamanho;
        List<EventoTimeline> eventos = temMais
                ? new ArrayList<>(encontrados.subList(0, tamanho))
                : encontrados;
        return new PaginaTimeline(eventos, pagina, temMais);
    }

    private EventoTimeline mapear(ResultSet linha, int indice) throws SQLException {
        String snapshot = linha.getString("descricao");
        return new EventoTimeline(
                linha.getObject("id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                linha.getObject("atendimento_id", UUID.class),
                linha.getString("tipo"),
                renderizar(linha, snapshot),
                OrigemEvento.valueOf(linha.getString("origem")),
                linha.getObject("ator_id", UUID.class),
                dados(linha.getString("dados_json")),
                linha.getTimestamp("criado_em").toInstant());
    }

    private static String renderizar(ResultSet linha, String fallback) throws SQLException {
        if (linha.getObject("ator_id", UUID.class) == null || linha.getString("ator_nome") == null) {
            return fallback;
        }
        String ator = linha.getString("ator_nome");
        return switch (linha.getString("tipo")) {
            case "MENSAGEM_ENVIADA" -> "Atendente " + ator + " enviou uma mensagem.";
            case "LEAD_TRANSFERIDO_POR_ENVIO" -> renderizarAssuncao(linha, ator, fallback);
            case "ATENDIMENTO_TRANSFERIDO" -> renderizarTransferencia(linha, ator, fallback);
            case "ATENDIMENTO_FINALIZADO" -> "Atendimento finalizado por " + ator + ".";
            default -> fallback;
        };
    }

    private static String renderizarAssuncao(ResultSet linha, String ator, String fallback)
            throws SQLException {
        Boolean tinhaAnterior = linha.getObject("tinha_dono_anterior", Boolean.class);
        if (tinhaAnterior == null) return fallback;
        if (!tinhaAnterior) {
            return "Atendente " + ator
                    + " enviou mensagem e assumiu o lead, que estava sem responsavel.";
        }
        String anterior = linha.getString("dono_anterior_nome");
        return anterior == null
                ? fallback
                : "Atendente " + ator + " enviou mensagem e assumiu o lead, antes de " + anterior + ".";
    }

    private static String renderizarTransferencia(ResultSet linha, String ator, String fallback)
            throws SQLException {
        Boolean deIa = linha.getObject("de_ia", Boolean.class);
        Boolean paraIa = linha.getObject("para_ia", Boolean.class);
        if (deIa == null || paraIa == null) return fallback;
        String origem = deIa ? "IA" : linha.getString("de_atendente_nome");
        String destino = paraIa ? "IA" : linha.getString("para_atendente_nome");
        if (origem == null || destino == null) return fallback;
        return "Atendimento transferido de " + origem + " para " + destino + " por " + ator + ".";
    }

    private Map<String, Object> dados(String valor) {
        try {
            return valor == null ? Map.of() : json.readValue(valor, MAPA);
        } catch (JsonProcessingException erro) {
            return new LinkedHashMap<>();
        }
    }
}
