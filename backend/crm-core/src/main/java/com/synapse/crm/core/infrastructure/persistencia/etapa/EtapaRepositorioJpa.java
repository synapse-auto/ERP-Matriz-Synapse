package com.synapse.crm.core.infrastructure.persistencia.etapa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.etapa.EtapaRepositorio;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;

/** Adaptador JPA das etapas do funil. */
@Repository
class EtapaRepositorioJpa implements EtapaRepositorio {

    private final EtapaJpaRepository jpa;

    EtapaRepositorioJpa(EtapaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<EtapaAtendimento> listarEmOrdem() {
        return jpa.findAllByOrderByOrdemAsc().stream().map(EtapaEntity::paraDominio).toList();
    }

    @Override
    public Optional<EtapaAtendimento> porId(UUID id) {
        return jpa.findById(id).map(EtapaEntity::paraDominio);
    }

    @Override
    public EtapaAtendimento salvar(EtapaAtendimento etapa) {
        EtapaEntity entidade = jpa.findById(etapa.id()).orElseGet(() -> new EtapaEntity(etapa));
        entidade.aplicar(etapa);
        return jpa.save(entidade).paraDominio();
    }

    @Override
    public void remover(UUID id) {
        jpa.deleteById(id);
    }
}
