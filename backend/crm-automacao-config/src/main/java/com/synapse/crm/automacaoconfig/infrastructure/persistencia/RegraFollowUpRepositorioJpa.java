package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.regras.RegraFollowUpRepositorio;
import com.synapse.crm.automacaoconfig.domain.regras.RegraFollowUp;

@Repository
class RegraFollowUpRepositorioJpa implements RegraFollowUpRepositorio {

    private final RegraFollowUpJpaRepository jpa;

    RegraFollowUpRepositorioJpa(RegraFollowUpJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<RegraFollowUp> listarAtivas() {
        return jpa.findByAtivoTrue().stream().map(RegraFollowUpEntity::paraDominio).toList();
    }
}
