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
                "INSERT INTO solicitacao_resumo_ia (solicitacao_id, lead_id, atendimento_id, status, solicitado_em, atualizado_em, atendente_id_no_pedido) "
                        + "VALUES (?, ?, ?, 'PENDENTE', ?, ?, (SELECT atendente_id FROM atendimento WHERE id = ?))",
                solicitacaoId,
                leadId,
                atendimentoId,
                Timestamp.from(solicitadoEm),
                Timestamp.from(solicitadoEm), atendimentoId);
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
    public boolean cicloVigente(UUID solicitacaoId) {
        TransacaoObrigatoria.exigir("validar ciclo de resumo por IA");
        return Boolean.TRUE.equals(chat.queryForObject("SELECT EXISTS (SELECT 1 FROM solicitacao_resumo_ia s "
                        + "JOIN atendimento a ON a.id = s.atendimento_id AND a.lead_id = s.lead_id "
                        + "WHERE s.solicitacao_id = ? AND a.status = 'EM_ATENDIMENTO' "
                        + "AND (s.atendente_id_no_pedido IS NULL OR s.atendente_id_no_pedido = a.atendente_id))",
                Boolean.class, solicitacaoId));
    }

    @Override
    public String diagnosticoDeEntrega(UUID solicitacaoId) {
        TransacaoObrigatoria.exigir("diagnosticar entrega de resumo por IA");
        return chat.query("SELECT 'WEBHOOK_RESULTADO_INCERTO' FROM outbox_evento "
                        + "WHERE id = ? AND tipo = 'automacao.resumo_ia.solicitar' "
                        + "AND esgotado_em IS NOT NULL AND ultimo_erro = 'WEBHOOK_RESULTADO_INCERTO'",
                (rs, row) -> rs.getString(1), solicitacaoId).stream().findFirst().orElse(null);
    }

    @Override
    public boolean falharEntregaSePendente(UUID solicitacaoId, String codigo, Instant quando) {
        TransacaoObrigatoria.exigir("falhar entrega de resumo ainda pendente");
        return chat.update("UPDATE solicitacao_resumo_ia SET status = 'FALHOU', atualizado_em = ?, "
                        + "erro_codigo = ?, erro_mensagem = NULL WHERE solicitacao_id = ? AND status = 'PENDENTE'",
                Timestamp.from(quando), codigo, solicitacaoId) == 1;
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
        // A leitura anterior pelo caso de uso pode envelhecer entre callbacks concorrentes.
        // O predicado no próprio UPDATE impede PROCESSANDO/FALHOU tardio de reabrir CONCLUIDO.
        String estadoAnterior = switch (status) {
            case PROCESSANDO -> " AND status = 'PENDENTE'";
            case CONCLUIDO, FALHOU -> " AND status IN ('PENDENTE', 'PROCESSANDO')";
            case PENDENTE -> " AND FALSE";
        };
        return chat.update(
                        "UPDATE solicitacao_resumo_ia SET status = ?, atualizado_em = ?, erro_codigo = ?, erro_mensagem = ? "
                                + "WHERE solicitacao_id = ? AND lead_id = ? AND atendimento_id = ?" + estadoAnterior,
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
