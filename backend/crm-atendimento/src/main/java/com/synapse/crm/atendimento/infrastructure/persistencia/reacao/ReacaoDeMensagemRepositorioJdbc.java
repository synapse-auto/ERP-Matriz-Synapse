package com.synapse.crm.atendimento.infrastructure.persistencia.reacao;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.reacao.ReacaoDeMensagemRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class ReacaoDeMensagemRepositorioJdbc implements ReacaoDeMensagemRepositorio {

    private final JdbcTemplate chat;

    ReacaoDeMensagemRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Map<Chave, List<ResumoDeReacao>> resumir(List<Chave> chaves, UUID usuarioId) {
        TransacaoObrigatoria.exigir("reacao.resumir");
        if (chaves == null || chaves.isEmpty()) {
            return Map.of();
        }
        StringBuilder sql = new StringBuilder(
                """
                SELECT mensagem_id, mensagem_enviada_em, emoji, COUNT(*)::int AS quantidade,
                       BOOL_OR(usuario_id = ?) AS reagi
                  FROM mensagem_reacao
                 WHERE (mensagem_id, mensagem_enviada_em) IN (
                """);
        List<Object> args = new ArrayList<>();
        args.add(usuarioId);
        for (int i = 0; i < chaves.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?)");
            args.add(chaves.get(i).mensagemId());
            args.add(Timestamp.from(chaves.get(i).enviadoEm()));
        }
        sql.append(") GROUP BY mensagem_id, mensagem_enviada_em, emoji ORDER BY quantidade DESC, emoji");
        Map<Chave, List<ResumoDeReacao>> agrupado = new LinkedHashMap<>();
        for (Chave chave : chaves) {
            agrupado.put(chave, new ArrayList<>());
        }
        chat.query(sql.toString(), (java.sql.ResultSet linha) -> {
            while (linha.next()) {
                Chave chave = new Chave(
                        linha.getObject("mensagem_id", UUID.class),
                        linha.getTimestamp("mensagem_enviada_em").toInstant());
                agrupado.computeIfAbsent(chave, ignorado -> new ArrayList<>())
                        .add(new ResumoDeReacao(
                                linha.getString("emoji"),
                                linha.getInt("quantidade"),
                                linha.getBoolean("reagi")));
            }
            return null;
        }, args.toArray());
        Map<Chave, List<ResumoDeReacao>> imutavel = new LinkedHashMap<>();
        agrupado.forEach((chave, itens) -> imutavel.put(chave, List.copyOf(itens)));
        return Map.copyOf(imutavel);
    }

    @Override
    public List<ResumoDeReacao> resumirUma(Chave chave, UUID usuarioId) {
        return resumir(List.of(chave), usuarioId).getOrDefault(chave, List.of());
    }

    @Override
    public boolean definir(Chave chave, UUID atendimentoId, UUID usuarioId, String emoji) {
        TransacaoObrigatoria.exigir("reacao.definir");
        try {
            int linhas = chat.update(
                    """
                    INSERT INTO mensagem_reacao (id, mensagem_id, mensagem_enviada_em, usuario_id, emoji)
                    SELECT ?, m.id, m.enviado_em, ?, ?
                      FROM mensagem m
                     WHERE m.id = ? AND m.enviado_em = ? AND m.atendimento_id = ?
                    ON CONFLICT (mensagem_id, mensagem_enviada_em, usuario_id)
                    DO UPDATE SET emoji = EXCLUDED.emoji
                    """,
                    UUID.randomUUID(),
                    usuarioId,
                    emoji,
                    chave.mensagemId(),
                    Timestamp.from(chave.enviadoEm()),
                    atendimentoId);
            return linhas > 0;
        } catch (DuplicateKeyException duplicada) {
            return chat.update(
                            """
                            UPDATE mensagem_reacao
                               SET emoji = ?
                             WHERE mensagem_id = ? AND mensagem_enviada_em = ? AND usuario_id = ?
                            """,
                            emoji,
                            chave.mensagemId(),
                            Timestamp.from(chave.enviadoEm()),
                            usuarioId)
                    > 0;
        }
    }

    @Override
    public void remover(Chave chave, UUID atendimentoId, UUID usuarioId) {
        TransacaoObrigatoria.exigir("reacao.remover");
        chat.update(
                """
                DELETE FROM mensagem_reacao r
                 USING mensagem m
                 WHERE r.mensagem_id = m.id
                   AND r.mensagem_enviada_em = m.enviado_em
                   AND m.id = ?
                   AND m.enviado_em = ?
                   AND m.atendimento_id = ?
                   AND r.usuario_id = ?
                """,
                chave.mensagemId(),
                Timestamp.from(chave.enviadoEm()),
                atendimentoId,
                usuarioId);
    }
}
