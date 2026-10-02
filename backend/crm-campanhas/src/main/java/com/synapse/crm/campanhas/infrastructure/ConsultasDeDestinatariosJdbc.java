package com.synapse.crm.campanhas.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.campanhas.application.ConsultasDeDestinatarios;
import com.synapse.crm.campanhas.application.Pagina;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Leituras de destinatarios no pool geral: listar e exportar nao disputam conexao com o chat. */
@Repository
class ConsultasDeDestinatariosJdbc implements ConsultasDeDestinatarios {

    private static final int TAMANHO_DO_LOTE_DE_LEITURA = 500;

    private static final String SELECT =
            """
            SELECT d.id, d.lead_id, l.nome, d.telefone, d.status, d.motivo, d.erro_codigo, d.enviado_em,
                   d.entregue_em, d.lido_em, d.respondeu_em, d.conferencia_em
              FROM campanha_template_destinatario d
              JOIN lead l ON l.id = d.lead_id
            """;

    private final NamedParameterJdbcTemplate geral;

    ConsultasDeDestinatariosJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource geralDataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(geralDataSource);
        jdbc.setFetchSize(TAMANHO_DO_LOTE_DE_LEITURA);
        this.geral = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public Pagina<Linha> listar(Filtro filtro, int pagina, int tamanho) {
        TransacaoObrigatoria.exigir("listar destinatarios da campanha");
        MapSqlParameterSource parametros = parametros(filtro).addValue("limite", tamanho).addValue("deslocamento", pagina * tamanho);
        List<Linha> itens = geral.query(
                SELECT + clausulas(filtro) + " ORDER BY d.id LIMIT :limite OFFSET :deslocamento",
                parametros,
                this::linha);
        Long total = geral.queryForObject(
                "SELECT count(*) FROM campanha_template_destinatario d" + clausulas(filtro), parametros, Long.class);
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0L : total);
    }

    @Override
    public void percorrer(Filtro filtro, Consumer<Linha> consumidor) {
        TransacaoObrigatoria.exigir("exportar destinatarios da campanha");
        geral.query(
                SELECT + clausulas(filtro) + " ORDER BY d.id",
                parametros(filtro),
                rs -> {
                    consumidor.accept(linha(rs, 0));
                });
    }

    private static String clausulas(Filtro filtro) {
        StringBuilder sql = new StringBuilder(" WHERE d.campanha_id = :campanha");
        if (filtro.status() != null) {
            sql.append(" AND d.status = :status");
        }
        if (filtro.motivo() != null) {
            sql.append(" AND d.motivo = :motivo");
        }
        if (filtro.soConferencia()) {
            sql.append(" AND d.conferencia_em IS NOT NULL");
        }
        return sql.toString();
    }

    private static MapSqlParameterSource parametros(Filtro filtro) {
        MapSqlParameterSource parametros = new MapSqlParameterSource().addValue("campanha", filtro.campanhaId());
        if (filtro.status() != null) {
            parametros.addValue("status", filtro.status().name());
        }
        if (filtro.motivo() != null) {
            parametros.addValue("motivo", filtro.motivo().name());
        }
        return parametros;
    }

    private Linha linha(ResultSet rs, int numero) throws SQLException {
        String motivo = rs.getString("motivo");
        return new Linha(
                rs.getObject("id", UUID.class),
                rs.getObject("lead_id", UUID.class),
                rs.getString("nome"),
                rs.getString("telefone"),
                StatusDoDestinatario.valueOf(rs.getString("status")),
                motivo == null ? null : MotivoDoDestinatario.valueOf(motivo),
                (Integer) rs.getObject("erro_codigo"),
                instante(rs, "enviado_em"),
                instante(rs, "entregue_em"),
                instante(rs, "lido_em"),
                instante(rs, "respondeu_em"),
                instante(rs, "conferencia_em"));
    }

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
