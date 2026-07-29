package com.synapse.crm.automacaoconfig.application.regras;

import java.util.List;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFidelizacao;

public interface RegraFidelizacaoRepositorio {
    List<RegraFidelizacao> listarAtivas();
}
