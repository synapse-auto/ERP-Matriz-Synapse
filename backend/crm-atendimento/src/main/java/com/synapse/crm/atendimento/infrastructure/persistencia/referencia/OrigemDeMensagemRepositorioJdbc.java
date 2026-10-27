package com.synapse.crm.atendimento.infrastructure.persistencia.referencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.RemetenteTipo;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class OrigemDeMensagemRepositorioJdbc implements OrigemDeMensagemRepositorio {

    private static final String SQL_BUSCAR =
            """
            SELECT m.id, m.atendimento_id, m.remetente_tipo, m.remetente_id, m.tipo, m.conteudo,
                   m.midia_url, m.midia_metadados, m.opcoes, m.status_entrega, m.enviado_em,
                   a.lead_id, l.nome AS lead_nome, u.nome AS remetente_nome, COALESCE(u.usuario_teste,FALSE) AS remetente_teste
              FROM mensagem m
              JOIN atendimento a ON a.id = m.atendimento_id
              JOIN lead l ON l.id = a.lead_id
              LEFT JOIN usuario u ON u.id = m.remetente_id
             WHERE m.id = ? AND m.enviado_em = ?
            """;

    private static final String SQL_BUSCAR_POR_WAMID =
            """
            SELECT m.id, m.atendimento_id, m.remetente_tipo, m.remetente_id, m.tipo, m.conteudo,
                   m.midia_url, m.midia_metadados, m.opcoes, m.status_entrega, m.enviado_em,
                   a.lead_id, l.nome AS lead_nome, u.nome AS remetente_nome, COALESCE(u.usuario_teste,FALSE) AS remetente_teste
              FROM mensagem_id_externo x
              JOIN mensagem m ON m.id = x.mensagem_id AND m.enviado_em = x.mensagem_enviada_em
              JOIN atendimento a ON a.id = m.atendimento_id
              JOIN lead l ON l.id = a.lead_id
              LEFT JOIN usuario u ON u.id = m.remetente_id
             WHERE x.wamid = ?
            """;

    private final JdbcTemplate chat;

    private static final String SQL_BUSCAR_POR_IDENTIDADE = """
            SELECT m.id, m.atendimento_id, m.remetente_tipo, m.remetente_id, m.tipo, m.conteudo,
                   m.midia_url, m.midia_metadados, m.opcoes, m.status_entrega, m.enviado_em,
                   a.lead_id, l.nome AS lead_nome, u.nome AS remetente_nome, COALESCE(u.usuario_teste,FALSE) AS remetente_teste
              FROM mensagem_correlacao_canal c
              JOIN mensagem m ON m.id=c.mensagem_id AND m.enviado_em=c.mensagem_enviada_em
                    AND m.atendimento_id=c.atendimento_id
              JOIN atendimento a ON a.id=m.atendimento_id
              JOIN lead l ON l.id=a.lead_id
              LEFT JOIN usuario u ON u.id=m.remetente_id
             WHERE c.provedor=? AND c.instancia=? AND c.namespace=? AND c.id_externo=?
               AND NOT c.conflitante AND c.namespace='WHATSAPP' AND l.id=?
            """;

    OrigemDeMensagemRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<OrigemDeMensagem> buscar(UUID mensagemId, Instant enviadoEm) {
        TransacaoObrigatoria.exigir("buscar origem");
        return chat.query(SQL_BUSCAR, this::mapear, mensagemId, Timestamp.from(enviadoEm)).stream()
                .findFirst();
    }

    @Override
    public Optional<OrigemDeMensagem> buscarPorWamid(String wamid) {
        TransacaoObrigatoria.exigir("buscar origem por identificador externo");
        return chat.query(SQL_BUSCAR_POR_WAMID, this::mapear, wamid).stream().findFirst();
    }

    @Override public Optional<OrigemDeMensagem> buscarPorIdentidade(
            com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal identidade, UUID leadId) {
        TransacaoObrigatoria.exigir("buscar origem por identidade do canal");
        var origens = chat.query(SQL_BUSCAR_POR_IDENTIDADE, this::mapear, identidade.provedor(), identidade.instancia(),
                identidade.namespace(), identidade.valor(), leadId);
        return origens.size() == 1 ? Optional.of(origens.getFirst()) : Optional.empty();
    }

    private OrigemDeMensagem mapear(ResultSet linha, int indice) throws SQLException {
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
        return new OrigemDeMensagem(
                mensagem,
                linha.getObject("lead_id", UUID.class),
                linha.getString("lead_nome"),
                linha.getString("remetente_nome"),linha.getBoolean("remetente_teste"));
    }
}
