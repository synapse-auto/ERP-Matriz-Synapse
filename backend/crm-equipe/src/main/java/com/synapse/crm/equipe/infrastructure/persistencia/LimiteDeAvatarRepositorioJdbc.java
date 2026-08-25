package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.usuario.LimiteDeAvatarRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Reutiliza a configuracao de tamanho de imagem ja usada pelos anexos de conversa. */
@Repository
class LimiteDeAvatarRepositorioJdbc implements LimiteDeAvatarRepositorio {

    private final JdbcTemplate jdbc;

    LimiteDeAvatarRepositorioJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<Long> limiteEmBytes() {
        try {
            return Optional.of(Long.parseLong(jdbc.queryForObject(
                    "SELECT valor FROM configuracao_automacao WHERE chave = ?",
                    String.class,
                    "anexo.tamanho_maximo_imagem_mb")) * 1024 * 1024);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
