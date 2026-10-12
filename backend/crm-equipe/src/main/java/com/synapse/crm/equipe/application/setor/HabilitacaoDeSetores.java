package com.synapse.crm.equipe.application.setor;

/** Porta da capacidade "setores": a decisão é por feature flag da instância, nunca pelo nome do cliente. */
public interface HabilitacaoDeSetores {

    boolean habilitada();

    /**
     * A mesma resposta, lida do instantaneo de flags que o cache de permissoes ja mantem (atraso maximo = intervalo de
     * revalidacao, 2s por padrao) em vez de uma ida ao banco por chamada. E a leitura do caminho quente (rodizio da IA),
     * onde "capacidade desligada" nao pode custar consulta. Telas de gestao continuam em {@link #habilitada()}, exata.
     */
    boolean habilitadaEmCache();

    default void exigir() {
        if (!habilitada()) {
            throw new SetoresDesligadosException();
        }
    }
}
