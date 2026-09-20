package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A leitura que os adaptadores de comando do webhook compartilham: um parametro de automacao, do
 * pool geral, normalizado — ausente, nulo ou em branco viram {@link Optional#empty()}, que e o que
 * faz o comando simplesmente nao ser reconhecido em vez de derrubar o processamento.
 *
 * <p>Existe para o {@code #reset} e o {@code #resetgeral} nao carregarem duas copias do mesmo SELECT.
 * Cada comando continua com a sua porta e a sua chave; o que se compartilha aqui e so o acesso.
 */
class ValorDeComandoDaAutomacao {

    private static final String SQL = "SELECT valor FROM configuracao_automacao WHERE chave = ?";

    private final JdbcTemplate geral;

    ValorDeComandoDaAutomacao(DataSource generalDataSource) {
        this.geral = new JdbcTemplate(generalDataSource);
    }

    Optional<String> porChave(String chave) {
        try {
            return Optional.ofNullable(geral.queryForObject(SQL, String.class, chave))
                    .map(String::trim)
                    .filter(valor -> !valor.isEmpty());
        } catch (EmptyResultDataAccessException erro) {
            return Optional.empty();
        }
    }
}
