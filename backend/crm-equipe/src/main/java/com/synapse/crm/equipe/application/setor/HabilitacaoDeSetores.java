package com.synapse.crm.equipe.application.setor;

/** Porta da capacidade "setores": a decisão é por feature flag da instância, nunca pelo nome do cliente. */
public interface HabilitacaoDeSetores {

    boolean habilitada();

    default void exigir() {
        if (!habilitada()) {
            throw new SetoresDesligadosException();
        }
    }
}
