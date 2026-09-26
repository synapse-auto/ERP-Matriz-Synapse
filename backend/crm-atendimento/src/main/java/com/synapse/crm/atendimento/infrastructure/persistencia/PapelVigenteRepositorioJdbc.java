package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.tempo_real.PapelVigenteRepositorio;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Lido no pool do chat, dentro da mesma transacao da revalidacao de assinatura. */
@Repository
class PapelVigenteRepositorioJdbc implements PapelVigenteRepositorio {

    private final JdbcTemplate chat;

    PapelVigenteRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<PapelUsuario> papelSeAtivo(UUID usuarioId) {
        return chat.query("SELECT papel::text FROM usuario WHERE id = ? AND ativo = TRUE",
                (r, i) -> PapelUsuario.valueOf(r.getString(1)), usuarioId).stream().findFirst();
    }
}
