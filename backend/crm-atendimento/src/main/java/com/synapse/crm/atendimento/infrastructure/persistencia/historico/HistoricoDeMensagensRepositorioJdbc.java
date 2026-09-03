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

import com.synapse.crm.atendimento.application.historico.ErroDeEntrega;
import com.synapse.crm.atendimento.application.historico.HistoricoDeMensagensRepositorio;
import com.synapse.crm.atendimento.application.historico.MensagemDoHistorico;
import com.synapse.crm.atendimento.domain.mensagem.CitacaoDeMensagem;
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
                    + " m.erro_entrega ->> 'codigo' AS erro_entrega_codigo,"
                    + " m.erro_entrega ->> 'titulo' AS erro_entrega_titulo,"
                    + " u.nome AS remetente_nome,"
                    + " r.tipo AS citacao_tipo_ref, r.origem_mensagem_id, r.citacao_autor,"
                    + " r.citacao_tipo, r.citacao_previa";

    private static final String JOINS =
            " FROM mensagem m"
                    + " JOIN atendimento origem ON origem.id = ?"
                    + " JOIN atendimento a ON a.id = m.atendimento_id AND a.lead_id = origem.lead_id"
                    + " LEFT JOIN usuario u ON u.id = m.remetente_id"
                    + " LEFT JOIN usuario ua ON ua.id = a.atendente_id"
                    + " LEFT JOIN mensagem_referencia r"
                    + "   ON r.mensagem_id = m.id AND r.mensagem_enviada_em = m.enviado_em";

    private static final String SQL_ULTIMAS = "SELECT " + COLUNAS + JOINS
            + " ORDER BY m.enviado_em DESC, m.id DESC LIMIT ?";

    private static final String SQL_ANTERIORES = "SELECT " + COLUNAS + JOINS
            + " WHERE (m.enviado_em, m.id) < (?, ?)"
            + " ORDER BY m.enviado_em DESC, m.id DESC LIMIT ?";

    private static final String SQL_DESDE = "SELECT " + COLUNAS + JOINS
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
                linha.getString("atendimento_responsavel_nome"),
                erroDeEntrega(linha),
                List.of(),
                citacaoDe(linha));
    }

    private static ErroDeEntrega erroDeEntrega(ResultSet linha) throws SQLException {
        String codigo = linha.getString("erro_entrega_codigo");
        String titulo = linha.getString("erro_entrega_titulo");
        return codigo == null && titulo == null
                ? null
                : new ErroDeEntrega(codigo == null ? null : Integer.valueOf(codigo), titulo);
    }

    private static CitacaoDeMensagem citacaoDe(ResultSet linha) throws SQLException {
        UUID origemId = linha.getObject("origem_mensagem_id", UUID.class);
        if (origemId == null) {
            return null;
        }
        return new CitacaoDeMensagem(
                origemId,
                linha.getString("citacao_tipo_ref"),
                linha.getString("citacao_autor"),
                linha.getString("citacao_tipo"),
                linha.getString("citacao_previa"));
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        Timestamp valor = linha.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
