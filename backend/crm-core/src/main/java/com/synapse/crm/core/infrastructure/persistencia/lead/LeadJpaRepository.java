package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio Spring Data da tabela {@code lead}.
 *
 * <p><strong>Porta dos fundos.</strong> Esta interface consegue ler lead sem nenhum filtro de
 * visibilidade — e por isso e pacote-privada e so pode ser usada por {@link LeadRepositorioJpa},
 * que sempre aplica a {@code VisibilidadeLeadSpecification} antes de delegar.
 *
 * <p>A restricao nao depende de ninguem lembrar: o modificador de acesso ja impede injecao a partir
 * de outro pacote, e {@code ArquiteturaTest} reprova o build se qualquer classe fora deste pacote
 * passar a depender dela.
 */
interface LeadJpaRepository extends JpaRepository<LeadEntity, UUID>, JpaSpecificationExecutor<LeadEntity> {}
