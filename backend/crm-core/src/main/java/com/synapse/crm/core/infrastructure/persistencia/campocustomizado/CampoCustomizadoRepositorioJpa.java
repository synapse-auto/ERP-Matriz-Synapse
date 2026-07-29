package com.synapse.crm.core.infrastructure.persistencia.campocustomizado;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;

/**
 * Adaptador sobre o pool geral (JPA). Metadado global, visivel a qualquer autenticado — nao ha
 * recorte de visibilidade aqui, ao contrario de {@code LeadRepositorioJpa}: campo customizado nao e
 * dado de um lead especifico, e conhecer o formulario nao vaza nada sensivel.
 */
@Repository
class CampoCustomizadoRepositorioJpa implements CampoCustomizadoRepositorio {

    private final CampoCustomizadoJpaRepository jpa;

    CampoCustomizadoRepositorioJpa(CampoCustomizadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<CampoCustomizado> listarTodos() {
        return jpa.findAllByOrderByOrdemAscChaveAsc().stream().map(CampoCustomizadoEntity::paraDominio).toList();
    }

    @Override
    public Optional<CampoCustomizado> porChave(String chave) {
        return jpa.findById(chave).map(CampoCustomizadoEntity::paraDominio);
    }
}
