package com.synapse.crm.automacaoconfig.domain;

/** Requisicao de marca que nao pode ser persistida com seguranca. */
public class MarcaDaInstanciaInvalidaException extends RuntimeException {

    public MarcaDaInstanciaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
