package com.synapse.crm.automacaoconfig.application;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoResumoIa;

public interface ConfiguracaoResumoIaRepositorio {
    ConfiguracaoResumoIa obter();
    ConfiguracaoResumoIa salvar(ConfiguracaoResumoIa configuracao);
}
