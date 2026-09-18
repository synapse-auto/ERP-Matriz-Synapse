package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.resumo.SolicitacaoResumoIaRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Adaptador JDBC da reserva de geração, no pool transacional do atendimento. */
@Repository
class SolicitacaoResumoIaRepositorioJdbc implements SolicitacaoResumoIaRepositorio {

    private static final String COLUNAS =
            "solicitacao_id, lead_id, atendimento_id, status, solicitado_em, atualizado_em, erro_codigo, erro_mensagem";
    private final JdbcTemplate chat;

    SolicitacaoResumoIaRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource dataSource) {
        this.chat = new JdbcTemplate(dataSource);
    }

    @Override
    public void criar(UUID solicitacaoId, UUID leadId, UUID atendimentoId, Instant solicitadoEm) {
        TransacaoObrigatoria.exigir("criar solicitacao de resumo por IA");
        chat.update(
                "INSERT INTO solicitacao_resumo_ia (solicitacao_id, lead_id, atendimento_id, status, solicitado_em, atualizado_em) "
                        + "VALUES (?, ?, ?, 'PENDENTE', ?, ?)",
                solicitacaoId,
                leadId,
                atendimentoId,
                Timestamp.from(solicitadoEm),
                Timestamp.from(solicitadoEm));
    }

    @Override
    public Optional<Solicitacao> porId(UUID solicitacaoId) {
        TransacaoObrigatoria.exigir("consultar solicitacao de resumo por IA");
        return chat.query(
                        "SELECT " + COLUNAS + " FROM solicitacao_resumo_ia WHERE solicitacao_id = ?",
                        (rs, row) -> mapear(rs),
                        solicitacaoId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Solicitacao> ultimaDoAtendimento(UUID atendimentoId) {
        TransacaoObrigatoria.exigir("consultar ultimo resumo por IA do atendimento");
        return chat.query(
                        "SELECT " + COLUNAS + " FROM solicitacao_resumo_ia "
                                + "WHERE atendimento_id = ? ORDER BY solicitado_em DESC LIMIT 1",
                        (rs, row) -> mapear(rs),
                        atendimentoId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean atualizarStatus(
            UUID solicitacaoId,
            UUID leadId,
            UUID atendimentoId,
            Status status,
            String erroCodigo,
            String erroMensagem,
            Instant atualizadoEm) {
        TransacaoObrigatoria.exigir("atualizar status de resumo por IA");
        return chat.update(
                        "UPDATE solicitacao_resumo_ia SET status = ?, atualizado_em = ?, erro_codigo = ?, erro_mensagem = ? "
                                + "WHERE solicitacao_id = ? AND lead_id = ? AND atendimento_id = ?",
                        status.name(),
                        Timestamp.from(atualizadoEm),
                        erroCodigo,
                        erroMensagem,
                        solicitacaoId,
                        leadId,
                        atendimentoId)
                == 1;
    }

    private Solicitacao mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Solicitacao(
                rs.getObject("solicitacao_id", UUID.class),
                rs.getObject("lead_id", UUID.class),
                rs.getObject("atendimento_id", UUID.class),
                Status.valueOf(rs.getString("status")),
                rs.getTimestamp("solicitado_em").toInstant(),
                rs.getTimestamp("atualizado_em").toInstant(),
                rs.getString("erro_codigo"),
                rs.getString("erro_mensagem"));
    }
}
