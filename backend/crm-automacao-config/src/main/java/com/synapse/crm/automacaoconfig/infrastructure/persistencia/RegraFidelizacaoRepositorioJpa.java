package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.regras.RegraFidelizacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.regras.RegraFidelizacao;

@Repository
class RegraFidelizacaoRepositorioJpa implements RegraFidelizacaoRepositorio {

    private final RegraFidelizacaoJpaRepository jpa;

    RegraFidelizacaoRepositorioJpa(RegraFidelizacaoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<RegraFidelizacao> listarAtivas() {
        return jpa.findByAtivoTrueOrderByDiasSemContatoAsc().stream().map(RegraFidelizacaoEntity::paraDominio).toList();
    }

    @Override public List<RegraFidelizacao> listarTodas() { return jpa.findAllByOrderByDiasSemContatoAsc().stream().map(RegraFidelizacaoEntity::paraDominio).toList(); }
    @Override public Optional<RegraFidelizacao> porId(UUID id) { return jpa.findById(id).map(RegraFidelizacaoEntity::paraDominio); }
    @Override public RegraFidelizacao salvar(RegraFidelizacao regra) {
        RegraFidelizacaoEntity entidade = jpa.findById(regra.id()).orElse(null);
        if (entidade == null) entidade = new RegraFidelizacaoEntity(regra); else entidade.atualizar(regra);
        return jpa.save(entidade).paraDominio();
    }
    @Override public void excluir(UUID id) { jpa.deleteById(id); }
}
