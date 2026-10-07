package com.synapse.crm.atendimento.application.painel;

import java.util.Optional;

/**
 * Porta para ler o estado do pool de conexoes do chat (E225, PR 5). O caso de uso da listagem do painel nao conhece o
 * Hikari: quem implementa e o modulo da aplicacao, que e dono do {@code DataSource}.
 */
public interface MedidorDoPoolDoChat {

    /** Estado agora, ou vazio se o pool ainda nao subiu. Nunca lanca e nunca abre conexao. */
    Optional<EstadoDoPool> estadoAtual();
}
