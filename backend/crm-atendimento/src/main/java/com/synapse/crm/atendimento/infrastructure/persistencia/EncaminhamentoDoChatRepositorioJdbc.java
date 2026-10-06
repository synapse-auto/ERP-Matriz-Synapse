package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.encaminhamentodochat.EncaminhamentoDoChatRepositorio;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Pool do chat, como o envio: a leitura de {@code mensagem} passa pela RLS com a identidade do
 * usuário, então o status só aparece para quem alcança o atendimento.
 */
@Repository
class EncaminhamentoDoChatRepositorioJdbc implements EncaminhamentoDoChatRepositorio {

    private static final String COLUNAS =
            "e.id, e.chave_idempotencia, e.usuario_id, e.conversa_id, e.mensagem_interna_id, e.atendimento_id, "
                    + "e.lead_id, e.mensagem_externa_id, e.mensagem_externa_enviada_em, e.tipo, "
                    + "e.transferiu_o_lead, e.convite_criado, e.criado_em";

    private final JdbcTemplate chat;

    EncaminhamentoDoChatRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<Encaminhamento> porChave(String chave) {
        TransacaoObrigatoria.exigir("consultar encaminhamento do chat por chave");
        return chat
                .query(
                        "SELECT " + COLUNAS + " FROM chat_interno_encaminhamento_cliente e "
                                + "WHERE e.chave_idempotencia = ?",
                        (rs, linha) -> mapear(rs),
                        chave)
                .stream()
                .findFirst();
    }

    @Override
    public Encaminhamento registrar(NovoEncaminhamento novo) {
        TransacaoObrigatoria.exigir("registrar encaminhamento do chat");
        return chat.queryForObject(
                "INSERT INTO chat_interno_encaminhamento_cliente AS e (chave_idempotencia, usuario_id, conversa_id, "
                        + "mensagem_interna_id, atendimento_id, lead_id, mensagem_externa_id, "
                        + "mensagem_externa_enviada_em, tipo, transferiu_o_lead, convite_criado) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING " + COLUNAS,
                (rs, linha) -> mapear(rs),
                novo.chave(),
                novo.usuarioId(),
                novo.conversaId(),
                novo.mensagemInternaId(),
                novo.atendimentoId(),
                novo.leadId(),
                novo.mensagemExternaId(),
                Timestamp.from(novo.mensagemExternaEnviadaEm()),
                novo.tipo(),
                novo.transferiuOLead(),
                novo.conviteCriado());
    }

    @Override
    public List<EncaminhamentoComStatus> daMensagem(UUID usuarioId, UUID mensagemInternaId) {
        TransacaoObrigatoria.exigir("listar encaminhamentos do chat");
        return chat.query(
                "SELECT " + COLUNAS + ", m.status_entrega, m.erro_entrega::text AS erro_entrega "
                        + "FROM chat_interno_encaminhamento_cliente e "
                        + "JOIN mensagem m ON m.id = e.mensagem_externa_id AND m.enviado_em = e.mensagem_externa_enviada_em "
                        + "WHERE e.usuario_id = ? AND e.mensagem_interna_id = ? "
                        + "ORDER BY e.criado_em DESC",
                (rs, linha) -> new EncaminhamentoComStatus(
                        mapear(rs), StatusEntrega.valueOf(rs.getString("status_entrega")), rs.getString("erro_entrega")),
                usuarioId,
                mensagemInternaId);
    }

    @Override
    public Optional<StatusEntrega> statusDaMensagemExterna(UUID mensagemExternaId, Instant enviadaEm) {
        TransacaoObrigatoria.exigir("consultar status da mensagem externa");
        return chat
                .query(
                        "SELECT status_entrega FROM mensagem WHERE id = ? AND enviado_em = ?",
                        (rs, linha) -> StatusEntrega.valueOf(rs.getString(1)),
                        mensagemExternaId,
                        Timestamp.from(enviadaEm))
                .stream()
                .findFirst();
    }

    @Override
    public List<DestinoEncontrado> buscarDestinosAbertos(String termo, int limite) {
        TransacaoObrigatoria.exigir("buscar destinos do encaminhamento do chat");
        String texto = termo == null ? "" : termo.trim();
        String digitos = texto.replaceAll("\\D", "");
        // Curingas do usuario valem como texto: LIKE/ILIKE interpretariam % e _ de dentro do termo.
        String padraoDoNome = texto.isEmpty() ? null : "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String padraoDoTelefone = digitos.length() >= 3 ? "%" + digitos + "%" : null;
        return chat.query(
                "SELECT a.id, l.nome, l.telefone, a.status::text AS status, u.nome AS responsavel "
                        + "FROM atendimento a JOIN lead l ON l.id = a.lead_id "
                        + "LEFT JOIN usuario u ON u.id = a.atendente_id "
                        + "WHERE a.status IN ('EM_IA'::status_atendimento, 'EM_ATENDIMENTO'::status_atendimento) "
                        + "AND (CAST(? AS text) IS NULL OR l.nome ILIKE CAST(? AS text) "
                        + "OR (CAST(? AS text) IS NOT NULL AND l.telefone LIKE CAST(? AS text))) "
                        + "ORDER BY l.ultima_interacao_em DESC NULLS LAST, a.id LIMIT ?",
                (rs, linha) -> new DestinoEncontrado(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        rs.getString("telefone"),
                        rs.getString("status"),
                        rs.getString("responsavel")),
                padraoDoNome,
                padraoDoNome,
                padraoDoTelefone,
                padraoDoTelefone,
                limite);
    }

    private static Encaminhamento mapear(ResultSet rs) throws SQLException {
        return new Encaminhamento(
                rs.getObject("id", UUID.class),
                rs.getString("chave_idempotencia"),
                rs.getObject("usuario_id", UUID.class),
                rs.getObject("conversa_id", UUID.class),
                rs.getObject("mensagem_interna_id", UUID.class),
                rs.getObject("atendimento_id", UUID.class),
                rs.getObject("lead_id", UUID.class),
                rs.getObject("mensagem_externa_id", UUID.class),
                rs.getTimestamp("mensagem_externa_enviada_em").toInstant(),
                rs.getString("tipo"),
                rs.getBoolean("transferiu_o_lead"),
                rs.getBoolean("convite_criado"),
                rs.getTimestamp("criado_em").toInstant());
    }
}
