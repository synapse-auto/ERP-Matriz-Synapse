package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.tempo_real.DestinatariosTempoRealRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Consulta tecnica que espelha a politica RLS vigente da tabela atendimento (V60). */
@Repository
class DestinatariosTempoRealRepositorioJdbc implements DestinatariosTempoRealRepositorio {

    private static final String SQL =
            """
            SELECT u.id
              FROM atendimento a
              JOIN usuario u ON u.ativo
             WHERE a.id = ?
               AND (
                    u.papel IN ('SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')
                    OR (u.papel = 'ATENDENTE' AND (
                        a.atendente_id = u.id
                        OR a.status IN ('EM_IA', 'FINALIZADO')
                        OR EXISTS (
                            SELECT 1
                              FROM atendimento_participante p
                             WHERE p.atendimento_id = a.id
                               AND p.usuario_id = u.id
                               AND p.saiu_em IS NULL
                        )
                    ))
               )
             ORDER BY u.id
            """;

    private final JdbcTemplate chat;

    DestinatariosTempoRealRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public List<UUID> listarAutorizados(UUID atendimentoId) {
        TransacaoObrigatoria.exigir("listarDestinatariosTempoReal");
        return chat.query(SQL, (linha, indice) -> linha.getObject(1, UUID.class), atendimentoId);
    }
}
