package com.synapse.crm.automacaoconfig.domain;

/**
 * Valor fora da faixa ({@code valor_min}/{@code valor_max}) ou incompativel com o {@code tipo} da
 * linha. Vira 400: a requisicao esta sintaticamente correta e semanticamente recusada.
 */
public class ConfiguracaoAutomacaoInvalidaException extends RuntimeException {

    public ConfiguracaoAutomacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
