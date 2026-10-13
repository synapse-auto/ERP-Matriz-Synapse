package com.synapse.crm.equipe.infrastructure.setor;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
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
    private final ResolvedorDePermissoesEfetivas permissoes;

    HabilitacaoDeSetoresPorFeatureFlag(
            ConsultaDeFuncionalidades funcionalidades, ResolvedorDePermissoesEfetivas permissoes) {
        this.funcionalidades = funcionalidades;
        this.permissoes = permissoes;
    }

    @Override
    public boolean habilitada() {
        return funcionalidades.habilitadas().contains(FUNCIONALIDADE);
    }

    @Override
    public boolean habilitadaEmCache() {
        return permissoes.flagsHabilitadas().contains(FUNCIONALIDADE);
    }
}
