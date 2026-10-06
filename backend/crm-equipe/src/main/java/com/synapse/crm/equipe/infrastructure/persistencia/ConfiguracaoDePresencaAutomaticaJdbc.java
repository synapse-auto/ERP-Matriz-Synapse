package com.synapse.crm.equipe.infrastructure.persistencia;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.usuario.ConfiguracaoDePresencaAutomatica;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Le a chave no pool geral, o mesmo de {@code ia.distribuicao.sequencial}: a configuracao nao disputa conexao com o
 * chat. Ausencia da linha (atualizacao que ainda nao aplicou a migration) conserva o comportamento de hoje: desligada.
 */
@Repository
class ConfiguracaoDePresencaAutomaticaJdbc implements ConfiguracaoDePresencaAutomatica {

    private static final String CHAVE = "presenca.automatica";
    private static final String SQL = "SELECT valor FROM configuracao_automacao WHERE chave = ?";

    private final JdbcTemplate jdbc;

    ConfiguracaoDePresencaAutomaticaJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.jdbc = new JdbcTemplate(generalDataSource);
    }

    @Override
    public boolean habilitada() {
        return jdbc.query(
                SQL, resultados -> resultados.next() && Boolean.parseBoolean(resultados.getString("valor")), CHAVE);
    }
}
