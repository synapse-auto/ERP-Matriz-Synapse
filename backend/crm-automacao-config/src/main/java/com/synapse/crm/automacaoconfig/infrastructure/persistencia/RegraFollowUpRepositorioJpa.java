package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        return jpa.findByAtivoTrueOrderByTempoMinutosAsc().stream().map(RegraFollowUpEntity::paraDominio).toList();
    }

    @Override public List<RegraFollowUp> listarTodas() { return jpa.findAllByOrderByTempoMinutosAsc().stream().map(RegraFollowUpEntity::paraDominio).toList(); }
    @Override public Optional<RegraFollowUp> porId(UUID id) { return jpa.findById(id).map(RegraFollowUpEntity::paraDominio); }
    @Override public RegraFollowUp salvar(RegraFollowUp regra) {
        RegraFollowUpEntity entidade = jpa.findById(regra.id()).orElse(null);
        if (entidade == null) entidade = new RegraFollowUpEntity(regra); else entidade.atualizar(regra);
        return jpa.save(entidade).paraDominio();
    }
    @Override public void excluir(UUID id) { jpa.deleteById(id); }
}
