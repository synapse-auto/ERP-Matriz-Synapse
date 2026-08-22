package com.synapse.crm.automacaoconfig.application.regras;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFollowUp;

public interface RegraFollowUpRepositorio {
    List<RegraFollowUp> listarAtivas();
    List<RegraFollowUp> listarTodas();
    Optional<RegraFollowUp> porId(UUID id);
    RegraFollowUp salvar(RegraFollowUp regra);
    void excluir(UUID id);
}
