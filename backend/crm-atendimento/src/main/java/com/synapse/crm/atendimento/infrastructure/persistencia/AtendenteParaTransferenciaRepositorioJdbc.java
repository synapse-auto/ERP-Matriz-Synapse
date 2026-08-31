package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.AtendenteDestinoInvalidoException.Motivo;
import com.synapse.crm.atendimento.application.AtendenteParaTransferenciaRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Lê o destino no mesmo pool/transação que grava lead e atendimento. */
@Repository
class AtendenteParaTransferenciaRepositorioJdbc implements AtendenteParaTransferenciaRepositorio {

    private static final String ELEGIVEL = "ativo = TRUE AND papel = 'ATENDENTE'";
    private static final String SQL = "SELECT id, nome FROM usuario WHERE id = ? AND " + ELEGIVEL;
    private static final String SQL_LISTAR =
            "SELECT id, nome FROM usuario WHERE " + ELEGIVEL + " ORDER BY nome, id";
    private static final String SQL_MOTIVO = """
            SELECT CASE
                WHEN NOT EXISTS (SELECT 1 FROM usuario WHERE id = ?) THEN 'INEXISTENTE'
                WHEN EXISTS (SELECT 1 FROM usuario WHERE id = ? AND ativo = FALSE) THEN 'INATIVO'
                ELSE 'PAPEL_NAO_ELEGIVEL'
            END
            """;

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

    @Override
    public java.util.List<Destino> listarAtivos() {
        return chat.query(
                SQL_LISTAR,
                (linha, indice) -> new Destino(linha.getObject("id", UUID.class), linha.getString("nome")));
    }

    @Override
    public Motivo motivoDaRecusa(UUID atendenteId) {
        String motivo = chat.queryForObject(SQL_MOTIVO, String.class, atendenteId, atendenteId);
        return Motivo.valueOf(motivo);
    }
}
