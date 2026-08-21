package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.AtendenteParaTransferenciaRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Lê o destino no mesmo pool/transação que grava lead e atendimento. */
@Repository
class AtendenteParaTransferenciaRepositorioJdbc implements AtendenteParaTransferenciaRepositorio {

    private static final String SQL =
            "SELECT id, nome FROM usuario WHERE id = ? AND ativo = TRUE AND papel = 'ATENDENTE'";

    private final JdbcTemplate chat;

    AtendenteParaTransferenciaRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public java.util.Optional<Destino> ativoAtendente(UUID atendenteId) {
        return chat.query(SQL, (linha, indice) -> new Destino(
                        linha.getObject("id", UUID.class), linha.getString("nome")), atendenteId)
                .stream()
                .findFirst();
    }
}
