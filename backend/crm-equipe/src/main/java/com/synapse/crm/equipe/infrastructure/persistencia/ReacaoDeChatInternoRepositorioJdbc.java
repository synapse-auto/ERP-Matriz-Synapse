package com.synapse.crm.equipe.infrastructure.persistencia;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.chat.ReacaoDeChatInternoRepositorio;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

@Repository
class ReacaoDeChatInternoRepositorioJdbc implements ReacaoDeChatInternoRepositorio {
    private final JdbcTemplate jdbc;

    ReacaoDeChatInternoRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, List<ResumoDeReacao>> resumir(List<UUID> mensagemIds, UUID usuarioId) {
        if (mensagemIds == null || mensagemIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", mensagemIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(usuarioId);
        args.addAll(mensagemIds);
        Map<UUID, List<ResumoDeReacao>> agrupado = new LinkedHashMap<>();
        for (UUID id : mensagemIds) {
            agrupado.put(id, new ArrayList<>());
        }
        jdbc.query(
                """
                SELECT mensagem_id, emoji, COUNT(*)::int AS quantidade, BOOL_OR(usuario_id = ?) AS reagi
                  FROM chat_interno_mensagem_reacao
                 WHERE mensagem_id IN (%s)
                 GROUP BY mensagem_id, emoji
                 ORDER BY quantidade DESC, emoji
                """.formatted(placeholders),
                (ResultSet linha) -> {
                    while (linha.next()) {
                        UUID mensagemId = linha.getObject("mensagem_id", UUID.class);
                        agrupado.computeIfAbsent(mensagemId, ignorado -> new ArrayList<>())
                                .add(new ResumoDeReacao(
                                        linha.getString("emoji"),
                                        linha.getInt("quantidade"),
                                        linha.getBoolean("reagi")));
                    }
                    return null;
                },
                args.toArray());
        Map<UUID, List<ResumoDeReacao>> imutavel = new LinkedHashMap<>();
        agrupado.forEach((id, itens) -> imutavel.put(id, List.copyOf(itens)));
        return Map.copyOf(imutavel);
    }

    @Override
    public List<ResumoDeReacao> resumirUma(UUID mensagemId, UUID usuarioId) {
        return resumir(List.of(mensagemId), usuarioId).getOrDefault(mensagemId, List.of());
    }

    @Override
    public boolean definir(UUID conversaId, UUID mensagemId, UUID usuarioId, String emoji) {
        try {
            int linhas = jdbc.update(
                    """
                    INSERT INTO chat_interno_mensagem_reacao (id, mensagem_id, usuario_id, emoji)
                    SELECT ?, m.id, ?, ?
                      FROM chat_interno_mensagem m
                     WHERE m.id = ? AND m.conversa_id = ?
                    ON CONFLICT (mensagem_id, usuario_id) DO UPDATE SET emoji = EXCLUDED.emoji
                    """,
                    UUID.randomUUID(),
                    usuarioId,
                    emoji,
                    mensagemId,
                    conversaId);
            return linhas > 0;
        } catch (DuplicateKeyException duplicada) {
            return jdbc.update(
                            """
                            UPDATE chat_interno_mensagem_reacao SET emoji = ?
                             WHERE mensagem_id = ? AND usuario_id = ?
                            """,
                            emoji,
                            mensagemId,
                            usuarioId)
                    > 0;
        }
    }

    @Override
    public void remover(UUID conversaId, UUID mensagemId, UUID usuarioId) {
        jdbc.update(
                """
                DELETE FROM chat_interno_mensagem_reacao r
                 USING chat_interno_mensagem m
                 WHERE r.mensagem_id = m.id
                   AND m.id = ?
                   AND m.conversa_id = ?
                   AND r.usuario_id = ?
                """,
                mensagemId,
                conversaId,
                usuarioId);
    }
}
