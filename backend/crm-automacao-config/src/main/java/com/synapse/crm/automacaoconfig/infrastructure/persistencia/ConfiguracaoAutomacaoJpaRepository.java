package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data cru. So {@link ConfiguracaoAutomacaoRepositorioJpa} o toca. */
interface ConfiguracaoAutomacaoJpaRepository extends JpaRepository<ConfiguracaoAutomacaoEntity, String> {}
