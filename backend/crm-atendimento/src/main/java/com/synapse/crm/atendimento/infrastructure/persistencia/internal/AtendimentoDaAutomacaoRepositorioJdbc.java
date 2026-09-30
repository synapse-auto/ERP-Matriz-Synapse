package com.synapse.crm.atendimento.infrastructure.persistencia.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.internal.AtendimentoDaAutomacaoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Consultas por chave (PK de mensagem_id_externo e idx_atendimento_lead); nunca le conteudo. */
@Repository
class AtendimentoDaAutomacaoRepositorioJdbc implements AtendimentoDaAutomacaoRepositorio {

    // remetente_tipo = 'LEAD': o wamid de uma SAIDA nao pode ancorar uma resposta.
    private static final String SQL_POR_MENSAGEM_RECEBIDA = """
            SELECT a.id, a.lead_id, a.status::text AS status
              FROM mensagem_id_externo x
              JOIN mensagem m ON m.id = x.mensagem_id AND m.enviado_em = x.mensagem_enviada_em
              JOIN atendimento a ON a.id = x.atendimento_id
             WHERE x.wamid = ? AND m.remetente_tipo = 'LEAD'
            """;

    private static final String SQL_ABERTO_DO_LEAD = """
            SELECT a.id, a.lead_id, a.status::text AS status
              FROM atendimento a
             WHERE a.lead_id = ? AND a.status IN ('EM_IA', 'EM_ATENDIMENTO')
             ORDER BY a.iniciado_em DESC, a.id DESC
             LIMIT 1
            """;

    private final JdbcTemplate chat;

    @Autowired
    AtendimentoDaAutomacaoRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<Vinculo> porMensagemRecebida(String idExternoDaEntrada) {
        TransacaoObrigatoria.exigir("resolver atendimento da mensagem recebida para a Automacao");
        return chat.query(SQL_POR_MENSAGEM_RECEBIDA, AtendimentoDaAutomacaoRepositorioJdbc::mapear, idExternoDaEntrada)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Vinculo> abertoDoLead(UUID leadId) {
        TransacaoObrigatoria.exigir("resolver atendimento aberto do lead para a Automacao");
        return chat.query(SQL_ABERTO_DO_LEAD, AtendimentoDaAutomacaoRepositorioJdbc::mapear, leadId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean leadExiste(UUID leadId) {
        TransacaoObrigatoria.exigir("verificar lead para a Automacao");
        return Boolean.TRUE.equals(chat.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM lead WHERE id = ?)", Boolean.class, leadId));
    }

    private static Vinculo mapear(ResultSet linha, int indice) throws SQLException {
        return new Vinculo(
                linha.getObject("id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                StatusAtendimento.valueOf(linha.getString("status")));
    }
}
