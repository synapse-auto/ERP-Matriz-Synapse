package com.synapse.crm.atendimento.infrastructure.persistencia;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.IdempotenciaDeMensagemRecebidaRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Índice global de IDs recebidos; a tabela de mensagens permanece particionada. */
@Repository
class IdempotenciaDeMensagemRecebidaRepositorioJdbc
        implements IdempotenciaDeMensagemRecebidaRepositorio {

    private final JdbcTemplate chat;

    IdempotenciaDeMensagemRecebidaRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public boolean reservarSeNova(String wamid) {
        TransacaoObrigatoria.exigir("reservar id de mensagem recebida");
        return chat.update(
                        "INSERT INTO mensagem_recebida_idempotencia (wamid) VALUES (?) "
                                + "ON CONFLICT (wamid) DO NOTHING",
                        wamid)
                == 1;
    }
}
