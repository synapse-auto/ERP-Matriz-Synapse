package com.synapse.crm.atendimento.infrastructure.persistencia.historico;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.historico.HistoricoDeMensagensRepositorio;
import com.synapse.crm.atendimento.application.historico.MensagemDoHistorico;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.RemetenteTipo;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Read model que resolve a autoria historica pelo identificador persistido na mensagem. */
@Repository
class HistoricoDeMensagensRepositorioJdbc implements HistoricoDeMensagensRepositorio {

    private static final String COLUNAS =
            "m.id, m.atendimento_id, a.iniciado_em AS atendimento_iniciado_em,"
                    + " a.finalizado_em AS atendimento_finalizado_em, ua.nome AS atendimento_responsavel_nome,"
                    + " m.remetente_tipo, m.remetente_id, m.tipo, m.conteudo,"
                    + " m.midia_url, m.midia_metadados, m.opcoes, m.status_entrega, m.enviado_em,"
                    + " u.nome AS remetente_nome";

    private static final String SQL_ULTIMAS = "SELECT " + COLUNAS
            + " FROM mensagem m"
            + " JOIN atendimento origem ON origem.id = ?"
            + " JOIN atendimento a ON a.id = m.atendimento_id AND a.lead_id = origem.lead_id"
            + " LEFT JOIN usuario u ON u.id = m.remetente_id"
            + " LEFT JOIN usuario ua ON ua.id = a.atendente_id"
            + " ORDER BY m.enviado_em DESC, m.id DESC LIMIT ?";

    private static final String SQL_ANTERIORES = "SELECT " + COLUNAS
            + " FROM mensagem m"
            + " JOIN atendimento origem ON origem.id = ?"
            + " JOIN atendimento a ON a.id = m.atendimento_id AND a.lead_id = origem.lead_id"
            + " LEFT JOIN usuario u ON u.id = m.remetente_id"
            + " LEFT JOIN usuario ua ON ua.id = a.atendente_id"
            + " WHERE (m.enviado_em, m.id) < (?, ?)"
            + " ORDER BY m.enviado_em DESC, m.id DESC LIMIT ?";

    private static final String SQL_DESDE = "SELECT " + COLUNAS
            + " FROM mensagem m"
            + " JOIN atendimento origem ON origem.id = ?"
            + " JOIN atendimento a ON a.id = m.atendimento_id AND a.lead_id = origem.lead_id"
            + " LEFT JOIN usuario u ON u.id = m.remetente_id"
            + " LEFT JOIN usuario ua ON ua.id = a.atendente_id"
            + " WHERE m.enviado_em > ?"
            + " ORDER BY m.enviado_em ASC";

    private final JdbcTemplate chat;

    HistoricoDeMensagensRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public List<MensagemDoHistorico> anteriores(
            UUID atendimentoId, Instant cursorEnviadoEm, UUID cursorId, int limite) {
        TransacaoObrigatoria.exigir("anteriores");
        if (cursorEnviadoEm == null) {
            return chat.query(SQL_ULTIMAS, this::mapear, atendimentoId, limite);
        }
        return chat.query(
                SQL_ANTERIORES,
                this::mapear,
                atendimentoId,
                Timestamp.from(cursorEnviadoEm),
                cursorId,
                limite);
    }

    @Override
    public List<MensagemDoHistorico> desde(UUID atendimentoId, Instant desde) {
        TransacaoObrigatoria.exigir("desde");
        return chat.query(SQL_DESDE, this::mapear, atendimentoId, Timestamp.from(desde));
    }

    private MensagemDoHistorico mapear(ResultSet linha, int indice) throws SQLException {
        RemetenteTipo tipoRemetente = RemetenteTipo.valueOf(linha.getString("remetente_tipo"));
        Mensagem mensagem = new Mensagem(
                linha.getObject("id", UUID.class),
                linha.getObject("atendimento_id", UUID.class),
                new Remetente(tipoRemetente, linha.getObject("remetente_id", UUID.class)),
                TipoMensagem.valueOf(linha.getString("tipo")),
                linha.getString("conteudo"),
                linha.getString("midia_url"),
                linha.getString("midia_metadados"),
                StatusEntrega.valueOf(linha.getString("status_entrega")),
                linha.getTimestamp("enviado_em").toInstant(),
                linha.getString("opcoes"));
        return new MensagemDoHistorico(
                mensagem,
                linha.getString("remetente_nome"),
                linha.getObject("atendimento_id", UUID.class),
                instante(linha, "atendimento_iniciado_em"),
                instante(linha, "atendimento_finalizado_em"),
                linha.getString("atendimento_responsavel_nome"));
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        Timestamp valor = linha.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
