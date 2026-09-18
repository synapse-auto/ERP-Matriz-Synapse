package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.chat.ChaveIdempotenciaMidiaChatInvalidaException;
import com.synapse.crm.equipe.application.chat.IdempotenciaDeMidiaChatRepositorio;

/** Índice estreito que evita duplicar mídia quando a resposta HTTP é perdida. */
@Repository
class IdempotenciaDeMidiaChatRepositorioJdbc implements IdempotenciaDeMidiaChatRepositorio {
    private static final String BUSCAR = """
            SELECT chave_idempotencia, remetente_id, conversa_id, impressao_requisicao, mensagem_id
              FROM chat_interno_midia_idempotencia
             WHERE chave_idempotencia = ?
            """;

    private final JdbcTemplate jdbc;

    IdempotenciaDeMidiaChatRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Reserva reservar(String chave, UUID remetenteId, UUID conversaId, String impressao) {
        int inserida = jdbc.update("""
                INSERT INTO chat_interno_midia_idempotencia
                    (chave_idempotencia, remetente_id, conversa_id, impressao_requisicao)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (chave_idempotencia) DO NOTHING
                """, chave, remetenteId, conversaId, impressao);
        if (inserida == 1) {
            return new Reserva(chave, remetenteId, conversaId, impressao, null, true);
        }

        Reserva existente = jdbc.queryForObject(BUSCAR, (rs, linha) -> new Reserva(
                rs.getString("chave_idempotencia"),
                rs.getObject("remetente_id", UUID.class),
                rs.getObject("conversa_id", UUID.class),
                rs.getString("impressao_requisicao"),
                rs.getObject("mensagem_id", UUID.class),
                false), chave);
        if (!existente.remetenteId().equals(remetenteId)
                || !existente.conversaId().equals(conversaId)
                || !existente.impressao().equals(impressao)) {
            throw new ChaveIdempotenciaMidiaChatInvalidaException();
        }
        return existente;
    }

    @Override
    public void concluir(String chave, UUID mensagemId) {
        jdbc.update("""
                UPDATE chat_interno_midia_idempotencia
                   SET mensagem_id = ?
                 WHERE chave_idempotencia = ?
                """, mensagemId, chave);
    }

    @Override
    public void cancelar(String chave) {
        jdbc.update("""
                DELETE FROM chat_interno_midia_idempotencia
                 WHERE chave_idempotencia = ? AND mensagem_id IS NULL
                """, chave);
    }
}
