package com.synapse.crm.atendimento.infrastructure.webhook;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A fila de entrada em cima de {@code webhook_entrada}, no pool do chat.
 *
 * <p>Pool do chat porque isto e o caminho de mensagem: e o cliente falando com a empresa. O bulkhead
 * protege o chat de relatorio pesado, nao o chat de si mesmo.
 */
@Repository
class WebhookEntradaRepositorioJdbc implements WebhookEntrada {

    /**
     * {@code ON CONFLICT DO NOTHING} e a idempotencia inteira.
     *
     * <p>O provedor reentrega — por timeout, por retry proprio, por reprocessamento de fila dele.
     * Fazer a deduplicacao com "SELECT, se nao existir INSERT" abriria uma corrida entre duas
     * reentregas simultaneas: as duas leem "nao existe" e as duas inserem. Aqui quem decide e a chave
     * primaria, dentro do banco, sem corrida possivel.
     */
    private static final String SQL_REGISTRAR =
            "INSERT INTO webhook_entrada (id_externo, provedor, payload, recebido_em)"
                    + " VALUES (?, ?, ?::jsonb, ?) ON CONFLICT (id_externo) DO NOTHING";

    private static final String SQL_RESERVAR =
            """
            SELECT id_externo, payload, tentativas
              FROM webhook_entrada
             WHERE processado_em IS NULL AND esgotado_em IS NULL
             ORDER BY recebido_em
             LIMIT ?
               FOR UPDATE SKIP LOCKED
            """;

    private static final String SQL_PROCESSADO =
            "UPDATE webhook_entrada SET processado_em = ?, ultimo_erro = NULL WHERE id_externo = ?";

    private static final String SQL_REAGENDAR =
            "UPDATE webhook_entrada SET tentativas = tentativas + 1, ultimo_erro = ?"
                    + " WHERE id_externo = ?";

    private static final String SQL_ESGOTAR =
            "UPDATE webhook_entrada SET tentativas = tentativas + 1, esgotado_em = ?, ultimo_erro = ?"
                    + " WHERE id_externo = ?";

    private static final String SQL_ESGOTADAS =
            "SELECT count(*) FROM webhook_entrada WHERE esgotado_em IS NOT NULL";

    private final JdbcTemplate chat;

    WebhookEntradaRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public boolean registrarSeNovo(
            String idExterno, String provedor, String payloadCru, Instant recebidoEm) {
        TransacaoObrigatoria.exigir("registrarSeNovo");
        // Zero linhas afetadas significa "ja tinhamos": e reentrega, nao erro.
        return chat.update(SQL_REGISTRAR, idExterno, provedor, payloadCru, Timestamp.from(recebidoEm))
                > 0;
    }

    @Override
    public List<Pendente> reservarPendentes(int limite) {
        TransacaoObrigatoria.exigir("reservarPendentes");
        return chat.query(
                SQL_RESERVAR,
                (linha, indice) -> new Pendente(
                        linha.getString("id_externo"),
                        linha.getString("payload"),
                        linha.getInt("tentativas")),
                limite);
    }

    @Override
    public void marcarProcessado(String idExterno, Instant quando) {
        TransacaoObrigatoria.exigir("marcarProcessado");
        chat.update(SQL_PROCESSADO, Timestamp.from(quando), idExterno);
    }

    @Override
    public void reagendar(String idExterno, String erro) {
        TransacaoObrigatoria.exigir("reagendar");
        chat.update(SQL_REAGENDAR, erro, idExterno);
    }

    @Override
    public void esgotar(String idExterno, Instant quando, String erro) {
        TransacaoObrigatoria.exigir("esgotar");
        chat.update(SQL_ESGOTAR, Timestamp.from(quando), erro, idExterno);
    }

    @Override
    public long quantidadeEsgotada() {
        TransacaoObrigatoria.exigir("quantidadeEsgotada");
        Long total = chat.queryForObject(SQL_ESGOTADAS, Long.class);
        return total == null ? 0L : total;
    }
}
