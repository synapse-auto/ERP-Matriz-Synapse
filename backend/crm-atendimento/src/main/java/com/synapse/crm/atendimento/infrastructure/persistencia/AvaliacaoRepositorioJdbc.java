package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
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
    public ResultadoSalvar salvarSeAusente(Avaliacao avaliacao) {
        TransacaoObrigatoria.exigir("avaliacao.salvarSeAusente");
        int inseridas = chat.update(
                """
                INSERT INTO avaliacao (id, atendimento_id, atendente_id, nota, comentario, criado_em)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (atendimento_id) DO NOTHING
                """,
                avaliacao.id(),
                avaliacao.atendimentoId(),
                avaliacao.atendenteId(),
                avaliacao.nota(),
                avaliacao.comentario(),
                Timestamp.from(avaliacao.criadoEm()));

        if (inseridas > 0) {
            return new ResultadoSalvar(avaliacao, true);
        }

        Avaliacao existente = porAtendimento(avaliacao.atendimentoId())
                .orElseThrow(() -> new AvaliacaoJaRegistradaException(avaliacao.atendimentoId()));
        return new ResultadoSalvar(existente, false);
    }

    @Override
    public Avaliacao salvar(Avaliacao avaliacao) {
        TransacaoObrigatoria.exigir("avaliacao.salvar");
        ResultadoSalvar resultado = salvarSeAusente(avaliacao);
        if (!resultado.inserido()) {
            throw new AvaliacaoJaRegistradaException(avaliacao.atendimentoId());
        }
        return resultado.avaliacao();
    }
}
