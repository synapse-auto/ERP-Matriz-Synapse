package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.AvaliacaoRepositorio;
import com.synapse.crm.atendimento.domain.avaliacao.Avaliacao;
import com.synapse.crm.atendimento.domain.avaliacao.AvaliacaoJaRegistradaException;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** JDBC no pool do chat: mesma transacao do {@code porId} do atendimento (RLS). */
@Repository
class AvaliacaoRepositorioJdbc implements AvaliacaoRepositorio {

    private final JdbcTemplate chat;

    AvaliacaoRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<Avaliacao> porAtendimento(UUID atendimentoId) {
        TransacaoObrigatoria.exigir("avaliacao.porAtendimento");
        return chat
                .query(
                        """
                        SELECT id, atendimento_id, atendente_id, nota, comentario, criado_em
                          FROM avaliacao
                         WHERE atendimento_id = ?
                        """,
                        (linha, indice) -> new Avaliacao(
                                linha.getObject("id", UUID.class),
                                linha.getObject("atendimento_id", UUID.class),
                                linha.getObject("atendente_id", UUID.class),
                                linha.getInt("nota"),
                                linha.getString("comentario"),
                                linha.getTimestamp("criado_em").toInstant()),
                        atendimentoId)
                .stream()
                .findFirst();
    }

    @Override
    public Avaliacao salvar(Avaliacao avaliacao) {
        TransacaoObrigatoria.exigir("avaliacao.salvar");
        try {
            chat.update(
                    """
                    INSERT INTO avaliacao (id, atendimento_id, atendente_id, nota, comentario, criado_em)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    avaliacao.id(),
                    avaliacao.atendimentoId(),
                    avaliacao.atendenteId(),
                    avaliacao.nota(),
                    avaliacao.comentario(),
                    Timestamp.from(avaliacao.criadoEm()));
        } catch (DuplicateKeyException duplicada) {
            throw new AvaliacaoJaRegistradaException(avaliacao.atendimentoId());
        }
        return avaliacao;
    }
}
