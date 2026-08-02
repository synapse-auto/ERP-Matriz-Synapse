package com.synapse.crm.sharedkernel.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um metodo de caso de uso como auditavel (E09a).
 *
 * <p>O aspecto que le esta anotacao mora em {@code crm-app} (unico modulo que enxerga todos os
 * outros), nunca no proprio caso de uso — por isso a anotacao e Java puro, sem nenhuma dependencia de
 * framework: quem a declara nao precisa saber como ou onde ela e processada.
 *
 * <p>O caso de uso anotado nao chama logger nenhum e nao sabe que esta sendo auditado. O aspecto
 * grava em {@code audit_log} depois que o metodo termina com sucesso, fora da transacao original —
 * uma falha ao gravar auditoria nunca pode ser o motivo de uma operacao de negocio falhar.
 *
 * <p>Convencao de captura de estado (ver {@link LocalizadorDeEstadoAnterior}): se o primeiro
 * argumento do metodo anotado for um {@code UUID}, ele e tratado como o id da entidade — usado tanto
 * para carregar o "antes" (via um {@link LocalizadorDeEstadoAnterior} registrado para
 * {@link #entidadeTipo()}) quanto, na ausencia de um retorno com id proprio, como {@code entidade_id}
 * da linha de auditoria. O "depois" e o valor de retorno do metodo (desembrulhando
 * {@code Optional}); metodos que devolvem {@code boolean} ou {@code void} (ex.: remocao) nao tem
 * "depois".
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

    /** Acao gravada em {@code audit_log.acao}, ex.: {@code "ATUALIZAR_TAG"}. */
    String acao();

    /** Tipo de entidade gravado em {@code audit_log.entidade_tipo}, ex.: {@code "TAG"}. */
    String entidadeTipo();
}
