package com.synapse.crm.automacaoconfig.domain;

/** Chave sem linha correspondente em {@code configuracao_automacao}. Vira 404. */
public class ConfiguracaoAutomacaoNaoEncontradaException extends RuntimeException {

    public ConfiguracaoAutomacaoNaoEncontradaException(String chave) {
        super("configuracao de automacao nao encontrada: '" + chave + "'");
    }
}
