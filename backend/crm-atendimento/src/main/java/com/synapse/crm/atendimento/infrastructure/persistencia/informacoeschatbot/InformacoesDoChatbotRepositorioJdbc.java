package com.synapse.crm.atendimento.infrastructure.persistencia.informacoeschatbot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.informacoeschatbot.InformacoesDoChatbotRepositorio;
import com.synapse.crm.atendimento.domain.informacoeschatbot.InformacoesDoChatbot;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Adaptador JDBC dos cards, no pool do atendimento: a RLS da tabela depende do contexto da transacao. */
@Repository
class InformacoesDoChatbotRepositorioJdbc implements InformacoesDoChatbotRepositorio {

    private final JdbcTemplate chat;

    InformacoesDoChatbotRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void inserir(
            UUID id, UUID atendimentoId, String chaveIdempotencia, String conteudo, Instant registradoEm) {
        TransacaoObrigatoria.exigir("registrar informacoes do chatbot");
        chat.update(
                "INSERT INTO atendimento_informacao_chatbot "
                        + "(id, atendimento_id, chave_idempotencia, conteudo, registrado_em) VALUES (?, ?, ?, ?, ?)",
                id,
                atendimentoId,
                chaveIdempotencia,
                conteudo,
                Timestamp.from(registradoEm));
    }

    @Override
    public List<InformacoesDoChatbot> recentesDoAtendimento(UUID atendimentoId, int limite) {
        TransacaoObrigatoria.exigir("listar informacoes do chatbot");
        List<InformacoesDoChatbot> maisRecentesPrimeiro = new ArrayList<>(chat.query(
                "SELECT id, atendimento_id, conteudo, registrado_em FROM atendimento_informacao_chatbot "
                        + "WHERE atendimento_id = ? ORDER BY registrado_em DESC, id DESC LIMIT ?",
                (rs, linha) -> new InformacoesDoChatbot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("atendimento_id", UUID.class),
                        rs.getString("conteudo"),
                        rs.getTimestamp("registrado_em").toInstant()),
                atendimentoId,
                limite));
        Collections.reverse(maisRecentesPrimeiro);
        return List.copyOf(maisRecentesPrimeiro);
    }
}
