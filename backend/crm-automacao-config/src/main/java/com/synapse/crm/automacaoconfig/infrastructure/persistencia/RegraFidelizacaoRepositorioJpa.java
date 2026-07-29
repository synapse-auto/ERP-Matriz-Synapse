package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;

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
        return jpa.findByAtivoTrue().stream().map(RegraFidelizacaoEntity::paraDominio).toList();
    }
}
