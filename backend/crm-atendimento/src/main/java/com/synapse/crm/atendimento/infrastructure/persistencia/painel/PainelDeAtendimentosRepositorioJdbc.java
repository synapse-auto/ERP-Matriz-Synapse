package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.application.painel.PainelDeAtendimentosRepositorio;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Le pelo pool do chat, igual a {@code AtendimentoRepositorioJdbc} — mesma origem de dados do
 * caminho critico, mesmo motivo (RNF-CRM-01): um relatorio pesado nao pode roubar conexao do chat, e
 * o inverso tambem vale, esta consulta nao pode competir pelo pool geral.
 *
 * <p>{@code mensagem} nao tem RLS propria (so {@code lead}/{@code atendimento}/{@code lembrete}/
 * {@code mensagem_programada}, desde a V12). Seguro aqui porque o {@code FROM} sempre parte de
 * {@code atendimento}, que tem a politica — os dois {@code LEFT JOIN LATERAL} em {@code mensagem} so
 * alcancam linhas de atendimentos que a RLS ja deixou passar.
 */
@Repository
class PainelDeAtendimentosRepositorioJdbc implements PainelDeAtendimentosRepositorio {

    // E97: a foto entregue pela integracao ganha da URL externa digitada na ficha. Mesmo
    // formato de ChatInternoRepositorioJdbc/FeedbackRepositorioJdbc com o avatar do usuario:
    // caminho relativo autenticado, nunca URL de storage.
    private static final String CAMPOS =
            """
            a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
            CASE WHEN l.foto_referencia IS NOT NULL
                 THEN '/api/v1/leads/' || l.id::text || '/foto'
                 ELSE l.foto_url END AS lead_foto_url,
            l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
            l.etapa_atendimento_id, et.nome AS etapa_nome,
            et.cor_visual AS etapa_cor, a.status, a.atendente_id, u.nome AS atendente_nome,
            a.iniciado_em AS iniciado_em,
            (
                SELECT aberto.id FROM atendimento aberto
                 WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
                 ORDER BY COALESCE((
                     SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
                      WHERE m_aberto.atendimento_id = aberto.id
                 ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
                 LIMIT 1
            ) AS atendimento_ativo_id,
            ultima.conteudo AS ultima_mensagem_preview,
            ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
            ultima.enviado_em AS ultima_mensagem_em,
            ultima_lead.enviado_em AS ultima_mensagem_do_lead_em,
            (
                SELECT COALESCE(SUM((
                    SELECT count(*) FROM mensagem nao_lida
                     WHERE nao_lida.atendimento_id = atendimento_do_lead.id
                       AND nao_lida.remetente_tipo = 'LEAD'
                       AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
                )), 0)
                  FROM atendimento atendimento_do_lead
                  LEFT JOIN atendimento_leitura leitura_do_lead
                    ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
                   AND leitura_do_lead.usuario_id = ?
                 WHERE atendimento_do_lead.lead_id = a.lead_id
            ) AS nao_lidas,
            ROW_NUMBER() OVER (
                PARTITION BY a.lead_id
                ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
            ) AS linha_do_lead
            """;

    private static final String ORIGEM =
            """
            FROM atendimento a
            JOIN lead l ON l.id = a.lead_id
            LEFT JOIN canal c ON c.id = a.canal_id
            LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
            LEFT JOIN usuario u ON u.id = a.atendente_id
            LEFT JOIN LATERAL (
                SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
                 WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
            ) ultima ON true
            LEFT JOIN LATERAL (
                SELECT enviado_em FROM mensagem m2
                 WHERE m2.atendimento_id = a.id AND m2.remetente_tipo = 'LEAD'
                 ORDER BY m2.enviado_em DESC LIMIT 1
            ) ultima_lead ON true
            """;

    private static final String ORDEM = " ORDER BY COALESCE(ultima_mensagem_em, iniciado_em) DESC";

    /**
     * As mesmas quatro condicoes de visao usadas em {@link #listar}, isoladas para que a contagem
     * (E17b §Bloco 6) monte {@code SELECT COUNT(*)} sobre exatamente o mesmo {@code WHERE} — nunca uma
     * segunda decisao de "o que e visivel" escrita a parte.
     */
    private static final String WHERE_ATIVOS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO'"
            + " AND visivel.atendente_id = ?)";

    private static final String WHERE_PENDENTES_PROPRIOS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel"
            + " WHERE m_visivel.atendimento_id = visivel.id ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel"
            + " ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO'"
            + " AND visivel.atendente_id = ? AND ultima_visivel.remetente_tipo = 'LEAD')";

    private static final String WHERE_PENDENTES_TODOS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel"
            + " WHERE m_visivel.atendimento_id = visivel.id ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel"
            + " ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO'"
            + " AND ultima_visivel.remetente_tipo = 'LEAD')";

    private static final String WHERE_POTENCIAIS = " WHERE EXISTS (SELECT 1 FROM atendimento visivel"
            + " WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA')";

    private static final String SQL_ATIVOS = agrupar(CAMPOS + ORIGEM + WHERE_ATIVOS);

    private static final String SQL_PENDENTES_PROPRIOS = agrupar(CAMPOS + ORIGEM + WHERE_PENDENTES_PROPRIOS);

    private static final String SQL_PENDENTES_TODOS = agrupar(CAMPOS + ORIGEM + WHERE_PENDENTES_TODOS);

    private static final String SQL_POTENCIAIS = agrupar(CAMPOS + ORIGEM + WHERE_POTENCIAIS);

    private static final String SQL_TODOS = agrupar(CAMPOS + ORIGEM);

    private static final String COLUNAS_CARTAO =
            "atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, "
                    + "etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, "
                    + "iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, "
                    + "ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, linha_do_lead";

    private static final String SQL_CONTAR_ATIVOS = contar(CAMPOS + ORIGEM + WHERE_ATIVOS);

    private static final String SQL_CONTAR_PENDENTES_PROPRIOS = contar(CAMPOS + ORIGEM + WHERE_PENDENTES_PROPRIOS);

    private static final String SQL_CONTAR_PENDENTES_TODOS = contar(CAMPOS + ORIGEM + WHERE_PENDENTES_TODOS);

    private static final String SQL_CONTAR_POTENCIAIS = contar(CAMPOS + ORIGEM + WHERE_POTENCIAIS);

    private static final String SQL_CONTAR_TODOS = contar(CAMPOS + ORIGEM);

    private static String agrupar(String consultaInterna) {
        return "SELECT " + COLUNAS_CARTAO + " FROM (SELECT " + consultaInterna + ") cartoes"
                + " WHERE linha_do_lead = 1" + ORDEM;
    }

    private static String contar(String consultaInterna) {
        return "SELECT COUNT(*) FROM (SELECT " + consultaInterna + ") cartoes"
                + " WHERE linha_do_lead = 1";
    }

    private static final RowMapper<CartaoAtendimento> MAPEADOR =
            PainelDeAtendimentosRepositorioJdbc::paraCartao;

    private final JdbcTemplate chat;

    PainelDeAtendimentosRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public List<CartaoAtendimento> listar(
            VisaoAtendimento visao, UUID usuarioId, boolean restritoAoProprioAtendente) {
        TransacaoObrigatoria.exigir("listar");
        return switch (visao) {
            case ATIVOS -> chat.query(SQL_ATIVOS, MAPEADOR, usuarioId, usuarioId);
            case PENDENTES -> restritoAoProprioAtendente
                    ? chat.query(SQL_PENDENTES_PROPRIOS, MAPEADOR, usuarioId, usuarioId)
                    : chat.query(SQL_PENDENTES_TODOS, MAPEADOR, usuarioId);
            case POTENCIAIS -> chat.query(SQL_POTENCIAIS, MAPEADOR, usuarioId);
            case TODOS -> chat.query(SQL_TODOS, MAPEADOR, usuarioId);
        };
    }

    @Override
    public List<CartaoAtendimento> listarPaginado(VisaoAtendimento visao, UUID usuarioId,
            boolean restritoAoProprioAtendente, Instant depoisDe, UUID depoisDoId, int limite) {
        TransacaoObrigatoria.exigir("listarPaginado");
        String filtro = switch (visao) {
            case ATIVOS -> WHERE_ATIVOS;
            case PENDENTES -> restritoAoProprioAtendente ? WHERE_PENDENTES_PROPRIOS : WHERE_PENDENTES_TODOS;
            case POTENCIAIS -> WHERE_POTENCIAIS;
            case TODOS -> "";
        };
        String consulta = "SELECT " + COLUNAS_CARTAO + " FROM (SELECT " + CAMPOS + ORIGEM + filtro
                + ") cartoes WHERE linha_do_lead = 1";
        List<Object> parametros = new java.util.ArrayList<>();
        parametros.add(usuarioId);
        if (visao == VisaoAtendimento.ATIVOS || (visao == VisaoAtendimento.PENDENTES && restritoAoProprioAtendente)) {
            parametros.add(usuarioId);
        }
        if (depoisDoId != null) {
            if (depoisDe == null) {
                consulta += " AND ultima_mensagem_em IS NULL AND atendimento_id < ?";
                parametros.add(depoisDoId);
            } else {
                consulta += " AND (ultima_mensagem_em < ? OR (ultima_mensagem_em = ? AND atendimento_id < ?)"
                        + " OR ultima_mensagem_em IS NULL)";
                parametros.add(Timestamp.from(depoisDe));
                parametros.add(Timestamp.from(depoisDe));
                parametros.add(depoisDoId);
            }
        }
        consulta += " ORDER BY ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT ?";
        parametros.add(Math.min(101, Math.max(1, limite)));
        return chat.query(consulta, MAPEADOR, parametros.toArray());
    }

    @Override
    public long contar(VisaoAtendimento visao, UUID usuarioId, boolean restritoAoProprioAtendente) {
        TransacaoObrigatoria.exigir("contar");
        return switch (visao) {
            case ATIVOS -> queryForCount(SQL_CONTAR_ATIVOS, usuarioId, usuarioId);
            case PENDENTES -> restritoAoProprioAtendente
                    ? queryForCount(SQL_CONTAR_PENDENTES_PROPRIOS, usuarioId, usuarioId)
                    : queryForCount(SQL_CONTAR_PENDENTES_TODOS, usuarioId);
            case POTENCIAIS -> queryForCount(SQL_CONTAR_POTENCIAIS, usuarioId);
            case TODOS -> queryForCount(SQL_CONTAR_TODOS, usuarioId);
        };
    }

    private long queryForCount(String sql, Object... parametros) {
        Long total = chat.queryForObject(sql, Long.class, parametros);
        return total == null ? 0 : total;
    }

    private static CartaoAtendimento paraCartao(ResultSet linha, int indice) throws SQLException {
        return new CartaoAtendimento(
                linha.getObject("atendimento_id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                linha.getString("lead_nome"),
                linha.getString("lead_foto_url"),
                linha.getString("lead_empresa"),
                linha.getString("lead_codigo"),
                linha.getString("canal_tipo"),
                linha.getObject("etapa_atendimento_id", UUID.class),
                linha.getString("etapa_nome"),
                linha.getString("etapa_cor"),
                StatusAtendimento.valueOf(linha.getString("status")),
                linha.getObject("atendente_id", UUID.class),
                linha.getString("atendente_nome"),
                linha.getObject("atendimento_ativo_id", UUID.class),
                linha.getString("ultima_mensagem_preview"),
                linha.getString("ultima_mensagem_remetente_tipo"),
                instante(linha, "ultima_mensagem_em"),
                instante(linha, "ultima_mensagem_do_lead_em"),
                linha.getLong("nao_lidas"));
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        Timestamp valor = linha.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
