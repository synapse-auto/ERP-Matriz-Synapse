package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.ConfiguracaoDoComandoResetGeralRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Adaptador de leitura do literal do {@code #resetgeral} (V79). */
@Repository
class ConfiguracaoDoComandoResetGeralRepositorioJdbc implements ConfiguracaoDoComandoResetGeralRepositorio {

    static final String CHAVE = "automacao.comando_reset_geral";

    private final ValorDeComandoDaAutomacao configuracoes;

    ConfiguracaoDoComandoResetGeralRepositorioJdbc(
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.configuracoes = new ValorDeComandoDaAutomacao(generalDataSource);
    }

    @Override
    public Optional<String> valor() {
        return configuracoes.porChave(CHAVE);
    }
}
