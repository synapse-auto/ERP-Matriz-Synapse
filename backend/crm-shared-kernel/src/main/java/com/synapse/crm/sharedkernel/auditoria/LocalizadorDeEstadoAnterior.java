package com.synapse.crm.sharedkernel.auditoria;

import java.util.Optional;
import java.util.UUID;

/**
 * Carrega o estado de uma entidade antes de um caso de uso {@link Auditable} altera-la.
 *
 * <p>Cada modulo de dominio que tem um caso de uso {@code @Auditable} operando sobre uma entidade
 * existente (atualizar, remover) declara um bean que implementa esta porta, reaproveitando o
 * repositorio que o proprio caso de uso ja usa — o mesmo dado, lido duas vezes (uma pelo aspecto,
 * antes de {@code proceed()}, outra pelo caso de uso), nunca duas fontes de verdade.
 *
 * <p>O aspecto de auditoria (em {@code crm-app}) coleta todos os beans deste tipo, de todos os
 * modulos, indexados por {@link #entidadeTipo()} — nenhum modulo de dominio precisa conhecer
 * {@code crm-app} para participar.
 */
public interface LocalizadorDeEstadoAnterior {

    /** Deve casar com {@link Auditable#entidadeTipo()} dos metodos que este localizador cobre. */
    String entidadeTipo();

    /** Vazio se a entidade nao existir (ex.: criacao, ou id invalido) — nunca lanca. */
    Optional<?> carregar(UUID id);
}
