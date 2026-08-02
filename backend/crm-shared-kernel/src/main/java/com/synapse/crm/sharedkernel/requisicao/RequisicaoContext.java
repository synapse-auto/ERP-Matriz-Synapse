package com.synapse.crm.sharedkernel.requisicao;

import java.util.Optional;

/**
 * IP de origem da requisicao HTTP em curso, se houver uma.
 *
 * <p>Casos de uso, jobs {@code @Scheduled} e consumidores de fila rodam sem requisicao HTTP —
 * {@link #ipOrigemSeHouver()} devolve vazio nesses casos, nunca lanca. Quem precisa do IP (hoje, so o
 * aspecto de auditoria da E09a) trata a ausencia como "IP desconhecido", nao como erro.
 */
public interface RequisicaoContext {

    Optional<String> ipOrigemSeHouver();
}
