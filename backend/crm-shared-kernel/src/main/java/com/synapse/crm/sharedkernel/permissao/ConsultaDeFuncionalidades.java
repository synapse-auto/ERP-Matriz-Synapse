package com.synapse.crm.sharedkernel.permissao;

import java.util.Set;

/**
 * Feature flags habilitadas na instancia, para quem precisa decidir sem estar numa requisicao HTTP.
 *
 * <p>O catalogo de permissoes (crm-equipe) nao pode depender de crm-automacao-config, onde a tabela
 * de flags e administrada. Capacidade cujo modulo esta desligado nao aparece no catalogo, nao entra
 * em payload e nunca e concedida.
 */
public interface ConsultaDeFuncionalidades {

    Set<String> habilitadas();
}
