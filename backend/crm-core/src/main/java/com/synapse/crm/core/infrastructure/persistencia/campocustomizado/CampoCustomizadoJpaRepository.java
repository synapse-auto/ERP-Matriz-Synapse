package com.synapse.crm.core.infrastructure.persistencia.campocustomizado;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data cru. So {@link CampoCustomizadoRepositorioJpa}, no mesmo pacote, pode
 * toca-lo — regra generica do ArchUnit ({@code so_o_adaptador_conversa_com_o_jpa_do_seu_agregado}).
 */
interface CampoCustomizadoJpaRepository extends JpaRepository<CampoCustomizadoEntity, String> {

    List<CampoCustomizadoEntity> findAllByOrderByOrdemAscChaveAsc();
}
