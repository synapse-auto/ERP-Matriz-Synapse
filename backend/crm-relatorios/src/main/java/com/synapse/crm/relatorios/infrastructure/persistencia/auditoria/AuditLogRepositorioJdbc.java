package com.synapse.crm.relatorios.infrastructure.persistencia.auditoria;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.synapse.crm.relatorios.application.auditoria.AuditLogRepositorio;
import com.synapse.crm.relatorios.domain.auditoria.FiltroDeAuditLog;
import com.synapse.crm.relatorios.domain.auditoria.LinhaDeAuditLog;

/**
 * Le {@code audit_log} com filtros combinados e paginacao real.
 *
 * <p>Ordenacao sempre por {@code criado_em DESC}, fixa — nunca aceitamos nome de coluna vindo de
 * {@link Pageable#getSort()}. Auditoria e cronologica por natureza (a mais recente primeiro e o unico
 * caso de uso real), e expor sort arbitrario abriria um vetor de injecao via nome de coluna que a
 * allowlist de {@code SerializadorAuditavel} nao cobre (aquela protege o conteudo gravado, nao a
 * consulta).
 */
@Repository
class AuditLogRepositorioJdbc implements AuditLogRepositorio {

    private static final String SELECT =
            "SELECT id, ator_id, ator_tipo::text, acao, entidade_tipo, entidade_id, lead_id, "
                    + "dados_antes::text, dados_depois::text, ip::text, criado_em FROM audit_log";
    private static final String CONTAGEM = "SELECT count(*) FROM audit_log";

    private static final RowMapper<LinhaDeAuditLog> MAPEADOR = AuditLogRepositorioJdbc::mapear;

    private final JdbcTemplate jdbc;

    AuditLogRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page<LinhaDeAuditLog> buscar(FiltroDeAuditLog filtro, Pageable pageable) {
        List<String> condicoes = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        adicionarCondicao(condicoes, parametros, "ator_id = ?", filtro.atorId());
        adicionarCondicao(condicoes, parametros, "acao = ?", filtro.acao());
        adicionarCondicao(condicoes, parametros, "entidade_tipo = ?", filtro.entidadeTipo());
        adicionarCondicao(condicoes, parametros, "entidade_id = ?", filtro.entidadeId());
        adicionarCondicao(condicoes, parametros, "lead_id = ?", filtro.leadId());
        if (filtro.de() != null) {
            condicoes.add("criado_em >= ?");
            parametros.add(Timestamp.from(filtro.de()));
        }
        if (filtro.ate() != null) {
            condicoes.add("criado_em <= ?");
            parametros.add(Timestamp.from(filtro.ate()));
        }

        String clausulaWhere = condicoes.isEmpty() ? "" : " WHERE " + String.join(" AND ", condicoes);

        long total = contar(clausulaWhere, parametros);
        List<LinhaDeAuditLog> linhas = selecionarPagina(clausulaWhere, parametros, pageable);
        return new PageImpl<>(linhas, pageable, total);
    }

    private long contar(String clausulaWhere, List<Object> parametros) {
        Long total = jdbc.queryForObject(CONTAGEM + clausulaWhere, Long.class, parametros.toArray());
        return total == null ? 0L : total;
    }

    private List<LinhaDeAuditLog> selecionarPagina(String clausulaWhere, List<Object> parametros, Pageable pageable) {
        List<Object> parametrosDaPagina = new ArrayList<>(parametros);
        parametrosDaPagina.add(pageable.getPageSize());
        parametrosDaPagina.add(pageable.getOffset());
        String consulta = SELECT + clausulaWhere + " ORDER BY criado_em DESC LIMIT ? OFFSET ?";
        return jdbc.query(consulta, MAPEADOR, parametrosDaPagina.toArray());
    }

    private static void adicionarCondicao(
            List<String> condicoes, List<Object> parametros, String condicao, Object valor) {
        if (valor != null) {
            condicoes.add(condicao);
            parametros.add(valor);
        }
    }

    private static LinhaDeAuditLog mapear(ResultSet linha, int indice) throws SQLException {
        return new LinhaDeAuditLog(
                linha.getLong("id"),
                (UUID) linha.getObject("ator_id"),
                linha.getString("ator_tipo"),
                linha.getString("acao"),
                linha.getString("entidade_tipo"),
                (UUID) linha.getObject("entidade_id"),
                (UUID) linha.getObject("lead_id"),
                linha.getString("dados_antes"),
                linha.getString("dados_depois"),
                linha.getString("ip"),
                linha.getTimestamp("criado_em").toInstant());
    }
}
