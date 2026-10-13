package com.synapse.crm.equipe.infrastructure.setor;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.setor.HabilitacaoDeSetores;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeFuncionalidades;

/**
 * Setores só existem nas instâncias que ligaram a feature flag {@value #FUNCIONALIDADE}. A decisão é por
 * capacidade, nunca pelo nome do cliente: um filho novo ganha o recurso ligando a flag, sem tocar no core.
 */
@Component
class HabilitacaoDeSetoresPorFeatureFlag implements HabilitacaoDeSetores {

    static final String FUNCIONALIDADE = "setores";

    private final ConsultaDeFuncionalidades funcionalidades;

    HabilitacaoDeSetoresPorFeatureFlag(ConsultaDeFuncionalidades funcionalidades) {
        this.funcionalidades = funcionalidades;
    }

    @Override
    public boolean habilitada() {
        return funcionalidades.habilitadas().contains(FUNCIONALIDADE);
    }
}
