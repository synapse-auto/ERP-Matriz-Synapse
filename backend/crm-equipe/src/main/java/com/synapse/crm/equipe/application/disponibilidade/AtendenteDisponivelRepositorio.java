package com.synapse.crm.equipe.application.disponibilidade;

import java.util.List;

import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;

public interface AtendenteDisponivelRepositorio {
    List<AtendenteDisponivelParaIa> listarDisponiveisParaIa();
}
