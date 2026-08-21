package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.IdempotenciaDeMensagemAutomacaoRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Índice global de wamid: fica fora da tabela particionada para a unicidade atravessar meses. */
@Repository
class IdempotenciaDeMensagemAutomacaoRepositorioJdbc implements IdempotenciaDeMensagemAutomacaoRepositorio {

    private static final String SQL_RESERVAR =
            "INSERT INTO mensagem_automacao_idempotencia "
                    + "(wamid, atendimento_id, mensagem_id, enviado_em) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (wamid) DO NOTHING";
    private static final String SQL_EXISTENTE =
            "SELECT wamid, atendimento_id, mensagem_id, enviado_em "
                    + "FROM mensagem_automacao_idempotencia WHERE wamid = ?";

    private final JdbcTemplate chat;

    IdempotenciaDeMensagemAutomacaoRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Reserva reservar(String wamid, UUID atendimentoId, UUID mensagemId, Instant enviadoEm) {
        TransacaoObrigatoria.exigir("reservar wamid");
        int inseridas = chat.update(
                SQL_RESERVAR, wamid, atendimentoId, mensagemId, Timestamp.from(enviadoEm));
        if (inseridas == 1) {
            return new Reserva(wamid, atendimentoId, mensagemId, enviadoEm, true);
        }
        return chat.queryForObject(SQL_EXISTENTE, (rs, linha) -> new Reserva(
                rs.getString("wamid"),
                rs.getObject("atendimento_id", UUID.class),
                rs.getObject("mensagem_id", UUID.class),
                rs.getTimestamp("enviado_em").toInstant(),
                false), wamid);
    }
}
