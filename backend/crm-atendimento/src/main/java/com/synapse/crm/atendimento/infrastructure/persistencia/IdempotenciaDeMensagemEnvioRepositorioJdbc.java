package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.ChaveIdempotenciaReutilizadaException;
import com.synapse.crm.atendimento.application.IdempotenciaDeMensagemEnvioRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Índice global do envio manual, fora da tabela particionada de mensagens. */
@Repository
class IdempotenciaDeMensagemEnvioRepositorioJdbc implements IdempotenciaDeMensagemEnvioRepositorio {

    private static final String SQL_BUSCAR =
            "SELECT chave_idempotencia, usuario_id, lead_id, atendimento_id, mensagem_id, "
                    + "mensagem_enviada_em, transferiu_lead FROM mensagem_envio_idempotencia "
                    + "WHERE chave_idempotencia = ?";
    private static final String SQL_RESERVAR =
            "INSERT INTO mensagem_envio_idempotencia "
                    + "(chave_idempotencia, usuario_id, lead_id, atendimento_id) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (chave_idempotencia) DO NOTHING";
    private static final String SQL_CONCLUIR =
            "UPDATE mensagem_envio_idempotencia SET mensagem_id = ?, mensagem_enviada_em = ?, "
                    + "transferiu_lead = ? WHERE chave_idempotencia = ? AND usuario_id = ?";

    private final JdbcTemplate chat;

    IdempotenciaDeMensagemEnvioRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public java.util.Optional<Reserva> existente(String chave, UUID usuarioId, UUID leadId) {
        TransacaoObrigatoria.exigir("consultar idempotencia de envio");
        try {
            Reserva reserva = chat.queryForObject(SQL_BUSCAR, (rs, linha) -> mapear(rs), chave);
            if (!reserva.usuarioId().equals(usuarioId) || !reserva.leadId().equals(leadId)) {
                throw new ChaveIdempotenciaReutilizadaException(chave, "envio manual", reserva.atendimentoId());
            }
            return java.util.Optional.of(reserva);
        } catch (EmptyResultDataAccessException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public Reserva reservar(String chave, UUID usuarioId, UUID leadId, UUID atendimentoId) {
        TransacaoObrigatoria.exigir("reservar idempotencia de envio");
        int inseridas = chat.update(SQL_RESERVAR, chave, usuarioId, leadId, atendimentoId);
        if (inseridas == 1) {
            return new Reserva(chave, usuarioId, leadId, atendimentoId, null, null, false, true);
        }
        Reserva existente = chat.queryForObject(SQL_BUSCAR, (rs, linha) -> mapear(rs), chave);
        if (!existente.usuarioId().equals(usuarioId)
                || !existente.leadId().equals(leadId)
                || !existente.atendimentoId().equals(atendimentoId)) {
            throw new ChaveIdempotenciaReutilizadaException(chave, "envio manual", existente.atendimentoId());
        }
        return existente;
    }

    @Override
    public void concluir(
            String chave, UUID usuarioId, UUID mensagemId, Instant enviadoEm, boolean transferiuOLead) {
        TransacaoObrigatoria.exigir("concluir idempotencia de envio");
        chat.update(SQL_CONCLUIR, mensagemId, Timestamp.from(enviadoEm), transferiuOLead, chave, usuarioId);
    }

    private static Reserva mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp enviadoEm = rs.getTimestamp("mensagem_enviada_em");
        return new Reserva(
                rs.getString("chave_idempotencia"),
                rs.getObject("usuario_id", UUID.class),
                rs.getObject("lead_id", UUID.class),
                rs.getObject("atendimento_id", UUID.class),
                rs.getObject("mensagem_id", UUID.class),
                enviadoEm == null ? null : enviadoEm.toInstant(),
                rs.getBoolean("transferiu_lead"),
                false);
    }
}
