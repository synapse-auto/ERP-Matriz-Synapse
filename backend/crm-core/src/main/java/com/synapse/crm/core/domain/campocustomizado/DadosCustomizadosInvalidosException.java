package com.synapse.crm.core.domain.campocustomizado;

/**
 * Falha ao validar {@code dados_customizados} contra os metadados de {@code campo_customizado} —
 * chave nao cadastrada, tipo incompativel, ou obrigatorio ausente. Vira 400: a requisicao esta
 * sintaticamente correta e semanticamente recusada, e quem integra corrige sozinho com a mensagem.
 */
public class DadosCustomizadosInvalidosException extends RuntimeException {

    public DadosCustomizadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
