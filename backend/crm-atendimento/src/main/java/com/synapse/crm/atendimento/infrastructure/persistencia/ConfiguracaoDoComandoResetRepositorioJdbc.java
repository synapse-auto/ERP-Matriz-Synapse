package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.ConfiguracaoDoComandoResetRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Adaptador de leitura do parametro de automacao usado pelo webhook de entrada. */
@Repository
class ConfiguracaoDoComandoResetRepositorioJdbc implements ConfiguracaoDoComandoResetRepositorio {

    static final String CHAVE = "automacao.comando_reset";
    private static final String SQL = "SELECT valor FROM configuracao_automacao WHERE chave = ?";

    private final JdbcTemplate geral;

    ConfiguracaoDoComandoResetRepositorioJdbc(
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.geral = new JdbcTemplate(generalDataSource);
    }

    @Override
    public Optional<String> valor() {
        try {
            return Optional.ofNullable(geral.queryForObject(SQL, String.class, CHAVE))
                    .map(String::trim)
                    .filter(valor -> !valor.isEmpty());
        } catch (EmptyResultDataAccessException erro) {
            return Optional.empty();
        }
    }
}
