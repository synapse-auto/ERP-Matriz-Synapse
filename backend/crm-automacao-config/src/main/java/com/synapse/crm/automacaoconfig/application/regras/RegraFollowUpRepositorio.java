package com.synapse.crm.automacaoconfig.application.regras;

import java.util.List;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFollowUp;

public interface RegraFollowUpRepositorio {
    List<RegraFollowUp> listarAtivas();
}
