package com.synapse.crm.atendimento.infrastructure.persistencia;

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

import com.synapse.crm.atendimento.application.MensagemRepositorio;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.RemetenteTipo;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Adaptador de mensagem sobre o pool do chat. O ponto mais quente do sistema.
 *
 * <p><b>Nada aqui sabe que a tabela e particionada.</b> O {@code INSERT} nomeia a tabela pai e
 * informa {@code enviado_em}; o Postgres decide a particao. Escolher a particao na aplicacao — pelo
 * nome do mes, por exemplo — faria uma falha do job de particionamento virar erro de escrita no
 * caminho critico, que e precisamente o que a particao {@code DEFAULT} da V5 existe para evitar.
 * Aqui a mensagem do cliente nao se perde por causa de janela de particao.
 *
 * <p>Um {@code INSERT} e nada mais: sem validacao de midia, sem chamada externa, sem resumo por IA.
 * Tudo isso e reacao {@code AFTER_COMMIT} ou vai para fila.
 */
@Repository
class MensagemRepositorioJdbc implements MensagemRepositorio {

    private static final String COLUNAS =
            "id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, midia_url,"
                    + " midia_metadados, status_entrega, enviado_em";

    private static final String SQL_REGISTRAR =
            """
            INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo,
                                  conteudo, midia_url, midia_metadados, status_entrega, enviado_em)
                 VALUES (?, ?, ?::remetente_tipo, ?, ?::tipo_mensagem, ?, ?, ?::jsonb,
                         ?::status_entrega, ?)
            """;

    private static final String SQL_ULTIMAS = "SELECT " + COLUNAS + " FROM mensagem"
            + " WHERE atendimento_id = ? ORDER BY enviado_em DESC LIMIT ?";

    // enviado_em no WHERE porque e a chave de particao: sem ela o PostgreSQL
    // varreria todas as particoes para achar uma unica linha.
    private static final String SQL_STATUS_ENTREGA =
            "UPDATE mensagem SET status_entrega = ?::status_entrega WHERE id = ? AND enviado_em = ?";

    private static final RowMapper<Mensagem> MAPEADOR = MensagemRepositorioJdbc::paraDominio;

    private final JdbcTemplate chat;

    MensagemRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Mensagem registrar(Mensagem mensagem) {
        TransacaoObrigatoria.exigir("registrar");
        chat.update(
                SQL_REGISTRAR,
                mensagem.id(),
                mensagem.atendimentoId(),
                mensagem.remetente().tipo().name(),
                mensagem.remetente().id(),
                mensagem.tipo().name(),
                mensagem.conteudo(),
                mensagem.midiaUrl(),
                mensagem.midiaMetadados(),
                mensagem.statusEntrega().name(),
                Timestamp.from(mensagem.enviadoEm()));
        return mensagem;
    }

    @Override
    public List<Mensagem> ultimasDoAtendimento(UUID atendimentoId, int limite) {
        TransacaoObrigatoria.exigir("ultimasDoAtendimento");
        return chat.query(SQL_ULTIMAS, MAPEADOR, atendimentoId, limite);
    }

    /**
     * Move a mensagem no ciclo de entrega. Chamado pelo publisher da outbox depois de o provedor
     * responder — {@code PENDENTE} vira {@code ENVIADO} ou {@code FALHOU}.
     *
     * <p>{@code enviado_em} entra no {@code WHERE} porque e a chave de particao: sem ela o PostgreSQL
     * varreria todas as particoes para achar uma linha.
     */
    @Override
    public void atualizarStatusEntrega(UUID mensagemId, Instant enviadoEm, StatusEntrega status) {
        TransacaoObrigatoria.exigir("atualizarStatusEntrega");
        chat.update(SQL_STATUS_ENTREGA, status.name(), mensagemId, Timestamp.from(enviadoEm));
    }

    private static Mensagem paraDominio(ResultSet linha, int indice) throws SQLException {
        RemetenteTipo tipoRemetente = RemetenteTipo.valueOf(linha.getString("remetente_tipo"));
        return new Mensagem(
                linha.getObject("id", UUID.class),
                linha.getObject("atendimento_id", UUID.class),
                new Remetente(tipoRemetente, linha.getObject("remetente_id", UUID.class)),
                TipoMensagem.valueOf(linha.getString("tipo")),
                linha.getString("conteudo"),
                linha.getString("midia_url"),
                linha.getString("midia_metadados"),
                StatusEntrega.valueOf(linha.getString("status_entrega")),
                linha.getTimestamp("enviado_em").toInstant());
    }
}
