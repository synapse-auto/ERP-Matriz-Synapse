package com.synapse.crm.equipe.application.disponibilidade;

import java.util.List;

import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.equipe.domain.disponibilidade.DiagnosticoDoRodizio;

public interface AtendenteDisponivelRepositorio {
    List<AtendenteDisponivelParaIa> listarDisponiveisParaIa();

    /**
     * Quantos usuarios sobram depois de cada filtro de {@link #listarDisponiveisParaIa()}, para explicar uma lista
     * vazia. So le contagens; nao altera quem e elegivel nem a ordem.
     */
    DiagnosticoDoRodizio diagnosticar();
}
