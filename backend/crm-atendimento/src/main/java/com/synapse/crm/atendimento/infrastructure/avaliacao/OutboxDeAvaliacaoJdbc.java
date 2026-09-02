package com.synapse.crm.atendimento.infrastructure.avaliacao;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.OutboxDeAvaliacao;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class OutboxDeAvaliacaoJdbc implements OutboxDeAvaliacao {
    static final String TIPO = "automacao.avaliacao.iniciar";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    OutboxDeAvaliacaoJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource dataSource, ObjectMapper json) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.json = json;
    }

    @Override
    public void enfileirar(UUID atendimentoId, UUID leadId, UUID atendenteId, String telefone, Instant quando) {
        TransacaoObrigatoria.exigir("enfileirarAvaliacao");
        UUID id = UUID.nameUUIDFromBytes((TIPO + ":" + atendimentoId).getBytes(StandardCharsets.UTF_8));
        // EV-08 §3/§8: o id da linha e a chave de idempotencia e agora viaja no corpo como
        // evento_id: deterministico por atendimento, igual em toda retentativa do mesmo evento.
        // operacao e finalizacao_em_massa sao constantes de proposito: o unico caminho que chega
        // aqui e a finalizacao individual (FinalizarAtendimentoUseCase.Origem.INDIVIDUAL). Sao
        // redundancia defensiva pedida pelo n8n, nao um interruptor para passar a disparar em lote.
        var payload = json.createObjectNode()
                .put("evento_id", id.toString())
                .put("atendimento_id", atendimentoId.toString())
                .put("lead_id", leadId.toString())
                .put("atendente_id", atendenteId.toString())
                .put("wa_id", telefone)
                .put("status_finalizacao", "FINALIZADO")
                .put("operacao", "FINALIZAR_INDIVIDUAL")
                .put("finalizacao_em_massa", false);
        jdbc.update("""
                INSERT INTO outbox_evento (id, tipo, payload, criado_em, proxima_tentativa_em)
                VALUES (?, ?, ?::jsonb, ?, ?) ON CONFLICT (id) DO NOTHING
                """, id, TIPO, payload.toString(), Timestamp.from(quando), Timestamp.from(quando));
    }

    @Override
    public List<Reserva> reservar(int limite, int maximoTentativas, Instant agora, Instant ate) {
        TransacaoObrigatoria.exigir("reservarAvaliacao");
        // Conta a tentativa na reserva, inclusive se o processo morrer antes de registrar o resultado.
        // Esgota reservas orfas que ja consumiram o limite; nunca transforma falha em publicado.
        jdbc.update("""
                UPDATE outbox_evento SET esgotado_em = ?, avaliacao_reserva_id = NULL,
                    ultimo_erro = 'LIMITE_APOS_RESERVA_EXPIRADA'
                WHERE tipo = ? AND publicado_em IS NULL AND esgotado_em IS NULL
                  AND tentativas >= ? AND proxima_tentativa_em <= ?
                """, Timestamp.from(agora), TIPO, maximoTentativas, Timestamp.from(agora));
        return jdbc.query("""
                WITH candidatos AS (
                    SELECT id FROM outbox_evento
                    WHERE tipo = ? AND publicado_em IS NULL AND esgotado_em IS NULL
                      AND proxima_tentativa_em <= ? AND tentativas < ?
                    ORDER BY proxima_tentativa_em, id LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_evento o SET proxima_tentativa_em = ?,
                    avaliacao_reserva_id = gen_random_uuid(), tentativas = o.tentativas + 1
                FROM candidatos c WHERE o.id = c.id
                RETURNING o.id, o.avaliacao_reserva_id, o.payload, o.tentativas
                """,
                (rs, n) -> new Reserva(rs.getObject("id", UUID.class),
                        rs.getObject("avaliacao_reserva_id", UUID.class), rs.getString("payload"),
                        rs.getInt("tentativas")),
                TIPO, Timestamp.from(agora), maximoTentativas, limite, Timestamp.from(ate));
    }

    @Override
    public boolean concluir(Reserva reserva, Instant quando) {
        TransacaoObrigatoria.exigir("concluirAvaliacao");
        return jdbc.update("""
                UPDATE outbox_evento SET publicado_em = ?, ultimo_erro = NULL, avaliacao_reserva_id = NULL
                WHERE id = ? AND tipo = ? AND avaliacao_reserva_id = ?
                  AND publicado_em IS NULL AND esgotado_em IS NULL AND proxima_tentativa_em > ?
                """, Timestamp.from(quando), reserva.eventoId(), TIPO, reserva.token(),
                Timestamp.from(quando)) == 1;
    }

    @Override
    public boolean falhar(Reserva reserva, Instant quando, Instant proxima, String motivo, boolean esgotada) {
        TransacaoObrigatoria.exigir("falharAvaliacao");
        return jdbc.update("""
                UPDATE outbox_evento SET esgotado_em = ?, proxima_tentativa_em = ?,
                    ultimo_erro = ?, avaliacao_reserva_id = NULL
                WHERE id = ? AND tipo = ? AND avaliacao_reserva_id = ?
                  AND publicado_em IS NULL AND esgotado_em IS NULL AND proxima_tentativa_em > ?
                """, esgotada ? Timestamp.from(quando) : null, Timestamp.from(proxima), motivo,
                reserva.eventoId(), TIPO, reserva.token(), Timestamp.from(quando)) == 1;
    }

    @Override
    public long esgotadas() {
        TransacaoObrigatoria.exigir("contarAvaliacoesEsgotadas");
        return jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo = ? AND esgotado_em IS NOT NULL",
                Long.class, TIPO);
    }
}
