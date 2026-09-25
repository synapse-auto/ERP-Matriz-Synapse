package com.synapse.crm.atendimento.infrastructure.persistencia.reacao;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.reacao.ReacaoDeMensagemRepositorio.Chave;
import com.synapse.crm.atendimento.application.reacao.ReacaoDoClienteRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class ReacaoDoClienteRepositorioJdbc implements ReacaoDoClienteRepositorio {

    /**
     * Um UPSERT so: o {@code WHERE} do conflito descarta evento mais antigo que o registrado (ordem
     * fora de sequencia) e evento que nao muda o emoji (reentrega), sem ler antes de escrever.
     */
    private static final String SQL_APLICAR =
            """
            INSERT INTO mensagem_reacao_cliente
                   (mensagem_id, mensagem_enviada_em, emoji, reagido_em, id_externo_evento)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (mensagem_id, mensagem_enviada_em) DO UPDATE
               SET emoji = EXCLUDED.emoji,
                   reagido_em = EXCLUDED.reagido_em,
                   id_externo_evento = EXCLUDED.id_externo_evento,
                   atualizado_em = now()
             WHERE mensagem_reacao_cliente.reagido_em <= EXCLUDED.reagido_em
               AND mensagem_reacao_cliente.emoji IS DISTINCT FROM EXCLUDED.emoji
            """;

    private final JdbcTemplate chat;

    ReacaoDoClienteRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public boolean aplicar(Chave chave, String emoji, Instant reagidoEm, String idExternoEvento) {
        TransacaoObrigatoria.exigir("reacao do cliente.aplicar");
        return chat.update(
                        SQL_APLICAR,
                        chave.mensagemId(),
                        Timestamp.from(chave.enviadoEm()),
                        emoji,
                        Timestamp.from(reagidoEm),
                        idExternoEvento)
                > 0;
    }

    @Override
    public Map<Chave, String> atuais(List<Chave> chaves) {
        TransacaoObrigatoria.exigir("reacao do cliente.atuais");
        if (chaves == null || chaves.isEmpty()) {
            return Map.of();
        }
        StringBuilder sql = new StringBuilder(
                """
                SELECT mensagem_id, mensagem_enviada_em, emoji
                  FROM mensagem_reacao_cliente
                 WHERE emoji IS NOT NULL
                   AND (mensagem_id, mensagem_enviada_em) IN (
                """);
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < chaves.size(); i++) {
            sql.append(i > 0 ? ", (?, ?)" : "(?, ?)");
            args.add(chaves.get(i).mensagemId());
            args.add(Timestamp.from(chaves.get(i).enviadoEm()));
        }
        sql.append(")");
        Map<Chave, String> encontradas = new HashMap<>();
        chat.query(sql.toString(), (java.sql.ResultSet linha) -> {
            encontradas.put(
                    new Chave(
                            linha.getObject("mensagem_id", UUID.class),
                            linha.getTimestamp("mensagem_enviada_em").toInstant()),
                    linha.getString("emoji"));
        }, args.toArray());
        return Map.copyOf(encontradas);
    }
}
