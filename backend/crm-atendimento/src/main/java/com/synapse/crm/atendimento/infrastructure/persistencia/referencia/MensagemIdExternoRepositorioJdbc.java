package com.synapse.crm.atendimento.infrastructure.persistencia.referencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class MensagemIdExternoRepositorioJdbc implements MensagemIdExternoRepositorio {

    private static final String SQL_GRAVAR =
            """
            INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)
            SELECT ?, id, enviado_em, atendimento_id
              FROM mensagem
             WHERE id = ? AND enviado_em = ?
            ON CONFLICT (wamid) DO NOTHING
            """;

    private static final String SQL_BUSCAR =
            "SELECT wamid FROM mensagem_id_externo WHERE mensagem_id = ? AND mensagem_enviada_em = ?";

    private final JdbcTemplate chat;

    MensagemIdExternoRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void gravar(String wamid, UUID mensagemId, Instant enviadoEm, UUID atendimentoId) {
        TransacaoObrigatoria.exigir("gravar id externo");
        if (wamid == null || wamid.isBlank()) {
            return;
        }
        chat.update(SQL_GRAVAR, wamid, mensagemId, Timestamp.from(enviadoEm));
    }

    @Override
    public Optional<String> wamidDe(UUID mensagemId, Instant enviadoEm) {
        TransacaoObrigatoria.exigir("buscar id externo");
        try {
            return Optional.ofNullable(chat.queryForObject(
                    SQL_BUSCAR, String.class, mensagemId, Timestamp.from(enviadoEm)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
