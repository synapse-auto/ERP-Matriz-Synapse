package com.synapse.crm.campanhas.domain;

/** Dado de campanha que o dominio nao aceita (limite acima do teto, variavel sem reserva, janela invertida...). */
public class CampanhaInvalidaException extends RuntimeException {

    public CampanhaInvalidaException(String detalhe) {
        super(detalhe);
    }
}
