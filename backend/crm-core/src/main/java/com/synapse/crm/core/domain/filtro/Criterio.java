package com.synapse.crm.core.domain.filtro;

/**
 * No da arvore de criterios (Composite).
 *
 * <p>Selado, pelo mesmo motivo de {@code VisibilidadeLead}: o interpretador casa exaustivamente
 * sobre as alternativas. Um tipo de no novo — "NAO", por exemplo — quebra o build ate ganhar
 * traducao para SQL. Sem isso, o no novo viraria "sem filtro" por omissao, que num sistema onde o
 * filtro compoe com regra de visibilidade e a falha mais cara possivel.
 *
 * @see CriterioSimples
 * @see CriterioComposto
 */
public sealed interface Criterio permits CriterioSimples, CriterioComposto {}
