package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.finalizacaomassa.AvisosDeFinalizacaoEmMassaRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Outbox dos avisos (contexto de servico). O lock de linha com SKIP LOCKED e o que impede entrega em dobro entre nos. */
@Repository
class AvisosDeFinalizacaoEmMassaRepositorioJdbc implements AvisosDeFinalizacaoEmMassaRepositorio {

    private static final String SQL_PROXIMO =
            """
            SELECT a.operacao_id, a.usuario_id, a.finalizados, a.tentativas,
                   o.finalizados AS total, o.ignorados, o.falhas
              FROM finalizacao_em_massa_aviso a
              JOIN finalizacao_em_massa o ON o.id = a.operacao_id
             WHERE a.estado = 'PENDENTE' AND a.tentar_apos <= ?
             ORDER BY a.criado_em
             LIMIT 1
             FOR UPDATE OF a SKIP LOCKED
            """;

    private static final String SQL_AFETADOS =
            """
            SELECT u.id, u.nome, a.finalizados
              FROM finalizacao_em_massa_aviso a JOIN usuario u ON u.id = a.usuario_id
             WHERE a.operacao_id = ?
             ORDER BY a.finalizados DESC, u.nome, u.id
            """;

    private final JdbcTemplate chat;

    AvisosDeFinalizacaoEmMassaRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<AvisoPendente> reservarProximo(Instant agora) {
        TransacaoObrigatoria.exigir("reservarProximoAviso");
        return chat
                .query(
                        SQL_PROXIMO,
                        (rs, i) -> {
                            UUID operacaoId = rs.getObject("operacao_id", UUID.class);
                            List<UsuarioAfetado> afetados = chat.query(
                                    SQL_AFETADOS,
                                    (r, n) -> new UsuarioAfetado(
                                            r.getObject("id", UUID.class), r.getString("nome"), r.getInt("finalizados")),
                                    operacaoId);
                            return new AvisoPendente(
                                    operacaoId,
                                    rs.getObject("usuario_id", UUID.class),
                                    rs.getInt("finalizados"),
                                    rs.getInt("total"),
                                    rs.getInt("ignorados"),
                                    rs.getInt("falhas"),
                                    afetados,
                                    rs.getInt("tentativas"));
                        },
                        Timestamp.from(agora))
                .stream()
                .findFirst();
    }

    @Override
    public void marcarEnviado(UUID operacaoId, UUID usuarioId, Instant quando) {
        TransacaoObrigatoria.exigir("marcarAvisoEnviado");
        chat.update(
                "UPDATE finalizacao_em_massa_aviso SET estado = 'ENVIADO', enviado_em = ? WHERE operacao_id = ? AND usuario_id = ?",
                Timestamp.from(quando),
                operacaoId,
                usuarioId);
    }

    @Override
    public boolean registrarFalha(UUID operacaoId, UUID usuarioId, String erro, Instant agora, int maximoDeTentativas) {
        TransacaoObrigatoria.exigir("registrarFalhaDoAviso");
        String estado = chat.queryForObject(
                """
                UPDATE finalizacao_em_massa_aviso
                   SET tentativas = tentativas + 1,
                       ultimo_erro = ?,
                       estado = CASE WHEN tentativas + 1 >= ? THEN 'ESGOTADO' ELSE estado END,
                       tentar_apos = ?::timestamptz + (interval '30 seconds' * (tentativas + 1))
                 WHERE operacao_id = ? AND usuario_id = ?
                RETURNING estado
                """,
                String.class,
                erro,
                maximoDeTentativas,
                Timestamp.from(agora),
                operacaoId,
                usuarioId);
        return "ESGOTADO".equals(estado);
    }
}
