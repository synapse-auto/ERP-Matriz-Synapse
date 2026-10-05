package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.finalizacaomassa.FiltroDeFinalizacao;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio;
import com.synapse.crm.atendimento.domain.finalizacaomassa.MotivoDoItemDeFinalizacao;
import com.synapse.crm.atendimento.domain.finalizacaomassa.StatusDaFinalizacaoEmMassa;
import com.synapse.crm.atendimento.domain.finalizacaomassa.StatusDoItemDeFinalizacao;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Pool do chat: as consultas do pedido herdam a RLS de quem pede; as do worker rodam em contexto de servico. */
@Repository
class FinalizacaoEmMassaRepositorioJdbc implements FinalizacaoEmMassaRepositorio {

    /** Ultima atividade = ultima mensagem, ou a abertura quando nao houve mensagem (criterio da inatividade). */
    private static final String ELEGIVEIS_DO_FILTRO =
            """
             FROM atendimento a
             LEFT JOIN LATERAL (
                   SELECT max(m.enviado_em) AS ultima FROM mensagem m WHERE m.atendimento_id = a.id
             ) ult ON TRUE
            WHERE a.status = 'EM_ATENDIMENTO'
              AND a.atendente_id = ANY (?)
              AND COALESCE(ult.ultima, a.iniciado_em) >= ?
              AND COALESCE(ult.ultima, a.iniciado_em) < ?
            """;

    private static final String SQL_CONTAR =
            "SELECT u.id AS atendente_id, u.nome, count(e.id) AS quantidade FROM usuario u"
                    + " LEFT JOIN (SELECT a.id, a.atendente_id" + ELEGIVEIS_DO_FILTRO
                    + ") e ON e.atendente_id = u.id WHERE u.id = ANY (?) GROUP BY u.id, u.nome ORDER BY u.nome, u.id";

    private static final String SQL_CONGELAR =
            "INSERT INTO finalizacao_em_massa_item (operacao_id, atendimento_id, atendente_id)"
                    + " SELECT ?, a.id, a.atendente_id" + ELEGIVEIS_DO_FILTRO + " ORDER BY a.iniciado_em, a.id LIMIT ?";

    private static final String SQL_INSERIR_OPERACAO =
            """
            INSERT INTO finalizacao_em_massa (id, solicitante_id, chave_idempotencia, impressao_filtros, atendente_ids,
                   periodo_inicio, periodo_fim, fuso, data_de, data_ate, hora_inicio, hora_fim, status, encontrados)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDENTE', 0)
            """;

    private static final String COLUNAS =
            "id, solicitante_id, atendente_ids, periodo_inicio, periodo_fim, fuso, data_de, data_ate, hora_inicio,"
                    + " hora_fim, status, encontrados, finalizados, ignorados, falhas, criada_em, iniciada_em,"
                    + " concluida_em, impressao_filtros";

    private static final String SQL_REIVINDICAR =
            "UPDATE finalizacao_em_massa o SET status = 'EM_ANDAMENTO', iniciada_em = COALESCE(o.iniciada_em, ?),"
                    + " lease_ate = ? WHERE o.id = (SELECT id FROM finalizacao_em_massa"
                    + " WHERE status IN ('PENDENTE', 'EM_ANDAMENTO') AND (lease_ate IS NULL OR lease_ate < ?)"
                    + " ORDER BY criada_em LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING " + COLUNAS;

    private static final String SQL_CONCLUIR =
            """
            UPDATE finalizacao_em_massa o
               SET status = 'CONCLUIDA', concluida_em = ?, lease_ate = NULL,
                   finalizados = c.f, ignorados = c.i, falhas = c.x
              FROM (SELECT count(*) FILTER (WHERE status = 'FINALIZADO') AS f,
                           count(*) FILTER (WHERE status = 'IGNORADO') AS i,
                           count(*) FILTER (WHERE status = 'FALHA') AS x,
                           count(*) FILTER (WHERE status = 'PENDENTE') AS p
                      FROM finalizacao_em_massa_item WHERE operacao_id = ?) c
             WHERE o.id = ? AND o.status <> 'CONCLUIDA' AND c.p = 0
            """;

    private static final String SQL_AVISAR_AFETADOS =
            "INSERT INTO finalizacao_em_massa_aviso (operacao_id, usuario_id, finalizados)"
                    + " SELECT operacao_id, atendente_id, count(*) FROM finalizacao_em_massa_item"
                    + " WHERE operacao_id = ? AND status = 'FINALIZADO' GROUP BY operacao_id, atendente_id"
                    + " ON CONFLICT DO NOTHING";

    private static final RowMapper<OperacaoDeFinalizacao> OPERACAO = FinalizacaoEmMassaRepositorioJdbc::operacao;

    private final JdbcTemplate chat;

    FinalizacaoEmMassaRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public List<ContagemPorAtendente> contarElegiveis(FiltroDeFinalizacao filtro) {
        TransacaoObrigatoria.exigir("contarElegiveis");
        return chat.query(
                SQL_CONTAR,
                ps -> {
                    ps.setArray(1, uuids(ps.getConnection(), filtro.atendenteIds()));
                    ps.setTimestamp(2, Timestamp.from(filtro.periodo().inicio()));
                    ps.setTimestamp(3, Timestamp.from(filtro.periodo().fim()));
                    ps.setArray(4, uuids(ps.getConnection(), filtro.atendenteIds()));
                },
                (rs, i) -> new ContagemPorAtendente(
                        rs.getObject("atendente_id", UUID.class), rs.getString("nome"), rs.getLong("quantidade")));
    }

    @Override
    public Set<UUID> usuariosExistentes(Collection<UUID> ids) {
        TransacaoObrigatoria.exigir("usuariosExistentes");
        List<UUID> achados = chat.query(
                "SELECT id FROM usuario WHERE id = ANY (?)",
                ps -> ps.setArray(1, uuids(ps.getConnection(), ids)),
                (rs, i) -> rs.getObject(1, UUID.class));
        return Set.copyOf(achados);
    }

    @Override
    public Optional<OperacaoDeFinalizacao> porChaveDeIdempotencia(UUID solicitanteId, String chave) {
        TransacaoObrigatoria.exigir("porChaveDeIdempotencia");
        return chat
                .query(
                        "SELECT " + COLUNAS + " FROM finalizacao_em_massa WHERE solicitante_id = ? AND chave_idempotencia = ?",
                        OPERACAO,
                        solicitanteId,
                        chave)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UUID> operacaoAtiva() {
        TransacaoObrigatoria.exigir("operacaoAtiva");
        return chat
                .query(
                        "SELECT id FROM finalizacao_em_massa WHERE status IN ('PENDENTE', 'EM_ANDAMENTO') LIMIT 1",
                        (rs, i) -> rs.getObject(1, UUID.class))
                .stream()
                .findFirst();
    }

    @Override
    public OperacaoDeFinalizacao criarCongelandoElegiveis(
            UUID solicitanteId, String chaveDeIdempotencia, FiltroDeFinalizacao filtro, int limite) {
        TransacaoObrigatoria.exigir("criarCongelandoElegiveis");
        UUID id = UUID.randomUUID();
        var periodo = filtro.periodo();
        try {
            chat.update(con -> {
                var ps = con.prepareStatement(SQL_INSERIR_OPERACAO);
                ps.setObject(1, id);
                ps.setObject(2, solicitanteId);
                ps.setString(3, chaveDeIdempotencia);
                ps.setString(4, filtro.impressao());
                ps.setArray(5, uuids(con, filtro.atendenteIds()));
                ps.setTimestamp(6, Timestamp.from(periodo.inicio()));
                ps.setTimestamp(7, Timestamp.from(periodo.fim()));
                ps.setString(8, periodo.fuso().getId());
                ps.setObject(9, periodo.de());
                ps.setObject(10, periodo.ate());
                ps.setObject(11, periodo.horaInicio());
                ps.setObject(12, periodo.horaFim());
                return ps;
            });
        } catch (DuplicateKeyException duplicada) {
            // Operacao ativa ja existente, ou a mesma chave criada em paralelo: o chamador decide qual.
            throw new OperacaoAtivaJaExisteException();
        }
        int congelados = chat.update(SQL_CONGELAR, ps -> {
            ps.setObject(1, id);
            ps.setArray(2, uuids(ps.getConnection(), filtro.atendenteIds()));
            ps.setTimestamp(3, Timestamp.from(periodo.inicio()));
            ps.setTimestamp(4, Timestamp.from(periodo.fim()));
            ps.setInt(5, limite);
        });
        chat.update("UPDATE finalizacao_em_massa SET encontrados = ? WHERE id = ?", congelados, id);
        return porId(id).orElseThrow();
    }

    @Override
    public Optional<OperacaoDeFinalizacao> porId(UUID id) {
        TransacaoObrigatoria.exigir("porId");
        return chat.query("SELECT " + COLUNAS + " FROM finalizacao_em_massa WHERE id = ?", OPERACAO, id)
                .stream()
                .findFirst();
    }

    @Override
    public ContagemDosItens contarItens(UUID operacaoId) {
        TransacaoObrigatoria.exigir("contarItens");
        int[] por = new int[4];
        chat.query(
                "SELECT status, count(*) FROM finalizacao_em_massa_item WHERE operacao_id = ? GROUP BY status",
                rs -> {
                    int n = rs.getInt(2);
                    switch (StatusDoItemDeFinalizacao.valueOf(rs.getString(1))) {
                        case PENDENTE -> por[0] = n;
                        case FINALIZADO -> por[1] = n;
                        case IGNORADO -> por[2] = n;
                        case FALHA -> por[3] = n;
                    }
                },
                operacaoId);
        return new ContagemDosItens(por[0], por[1], por[2], por[3]);
    }

    @Override
    public List<ItemDeFinalizacao> itens(UUID operacaoId, StatusDoItemDeFinalizacao status, int limite, int deslocamento) {
        TransacaoObrigatoria.exigir("itens");
        return chat.query(
                """
                SELECT i.atendimento_id, i.atendente_id, u.nome AS atendente_nome, l.nome AS lead_nome,
                       i.status, i.motivo, i.processado_em
                  FROM finalizacao_em_massa_item i
                  JOIN usuario u ON u.id = i.atendente_id
                  LEFT JOIN atendimento a ON a.id = i.atendimento_id
                  LEFT JOIN lead l ON l.id = a.lead_id
                 WHERE i.operacao_id = ? AND (?::text IS NULL OR i.status = ?)
                 ORDER BY i.processado_em NULLS LAST, i.atendimento_id
                 LIMIT ? OFFSET ?
                """,
                (rs, n) -> new ItemDeFinalizacao(
                        rs.getObject("atendimento_id", UUID.class),
                        rs.getObject("atendente_id", UUID.class),
                        rs.getString("atendente_nome"),
                        rs.getString("lead_nome"),
                        StatusDoItemDeFinalizacao.valueOf(rs.getString("status")),
                        rs.getString("motivo") == null ? null : MotivoDoItemDeFinalizacao.valueOf(rs.getString("motivo")),
                        instante(rs, "processado_em")),
                operacaoId,
                status == null ? null : status.name(),
                status == null ? null : status.name(),
                limite,
                deslocamento);
    }

    @Override
    public List<OperacaoDeFinalizacao> recentes(UUID solicitanteId, int limite) {
        TransacaoObrigatoria.exigir("recentes");
        return chat.query(
                "SELECT " + COLUNAS + " FROM finalizacao_em_massa WHERE solicitante_id = ? ORDER BY criada_em DESC LIMIT ?",
                OPERACAO,
                solicitanteId,
                limite);
    }

    // --- worker ------------------------------------------------------------------------------------

    @Override
    public Optional<OperacaoDeFinalizacao> reivindicarProxima(Instant agora, Duration lease) {
        TransacaoObrigatoria.exigir("reivindicarProxima");
        return chat
                .query(
                        SQL_REIVINDICAR,
                        OPERACAO,
                        Timestamp.from(agora),
                        Timestamp.from(agora.plus(lease)),
                        Timestamp.from(agora))
                .stream()
                .findFirst();
    }

    @Override
    public List<ItemPendente> proximosPendentes(UUID operacaoId, int limite) {
        TransacaoObrigatoria.exigir("proximosPendentes");
        return chat.query(
                "SELECT operacao_id, atendimento_id, atendente_id FROM finalizacao_em_massa_item"
                        + " WHERE operacao_id = ? AND status = 'PENDENTE' ORDER BY atendimento_id LIMIT ?",
                (rs, i) -> new ItemPendente(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)),
                operacaoId,
                limite);
    }

    @Override
    public boolean marcarItem(
            UUID operacaoId, UUID atendimentoId, StatusDoItemDeFinalizacao status,
            MotivoDoItemDeFinalizacao motivo, Instant quando) {
        TransacaoObrigatoria.exigir("marcarItem");
        return chat.update(
                        "UPDATE finalizacao_em_massa_item SET status = ?, motivo = ?, processado_em = ?"
                                + " WHERE operacao_id = ? AND atendimento_id = ? AND status = 'PENDENTE'",
                        status.name(),
                        motivo == null ? null : motivo.name(),
                        Timestamp.from(quando),
                        operacaoId,
                        atendimentoId)
                == 1;
    }

    @Override
    public boolean concluirSeNaoHaPendentes(UUID operacaoId, Instant agora) {
        TransacaoObrigatoria.exigir("concluirSeNaoHaPendentes");
        boolean concluiu = chat.update(SQL_CONCLUIR, Timestamp.from(agora), operacaoId, operacaoId) == 1;
        if (concluiu) {
            chat.update(SQL_AVISAR_AFETADOS, operacaoId);
        }
        return concluiu;
    }

    @Override
    public void liberarLease(UUID operacaoId) {
        TransacaoObrigatoria.exigir("liberarLease");
        chat.update("UPDATE finalizacao_em_massa SET lease_ate = NULL WHERE id = ? AND status <> 'CONCLUIDA'", operacaoId);
    }

    // --- mapeamento -----------------------------------------------------------------------------------

    private static java.sql.Array uuids(Connection con, Collection<UUID> ids) throws SQLException {
        return con.createArrayOf("uuid", ids.toArray());
    }

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp ts = rs.getTimestamp(coluna);
        return ts == null ? null : ts.toInstant();
    }

    private static OperacaoDeFinalizacao operacao(ResultSet rs, int linha) throws SQLException {
        Object[] ids = (Object[]) rs.getArray("atendente_ids").getArray();
        return new OperacaoDeFinalizacao(
                rs.getObject("id", UUID.class),
                rs.getObject("solicitante_id", UUID.class),
                java.util.Arrays.stream(ids).map(o -> (UUID) o).toList(),
                instante(rs, "periodo_inicio"),
                instante(rs, "periodo_fim"),
                rs.getString("fuso"),
                rs.getObject("data_de", LocalDate.class),
                rs.getObject("data_ate", LocalDate.class),
                rs.getObject("hora_inicio", LocalTime.class),
                rs.getObject("hora_fim", LocalTime.class),
                StatusDaFinalizacaoEmMassa.valueOf(rs.getString("status")),
                rs.getInt("encontrados"),
                rs.getInt("finalizados"),
                rs.getInt("ignorados"),
                rs.getInt("falhas"),
                instante(rs, "criada_em"),
                instante(rs, "iniciada_em"),
                instante(rs, "concluida_em"),
                rs.getString("impressao_filtros"));
    }
}
