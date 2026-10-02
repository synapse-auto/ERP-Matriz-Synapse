package com.synapse.crm.campanhas.domain;

/** Dado de campanha que o dominio nao aceita (limite acima do teto, variavel sem reserva, janela invertida...). */
public class CampanhaInvalidaException extends RuntimeException {

    public static final String CODIGO_PADRAO = "CAMPANHA_INVALIDA";

    private final String codigo;

    public CampanhaInvalidaException(String detalhe) {
        this(CODIGO_PADRAO, detalhe);
    }

    /** @param codigo identificador estavel que a interface traduz pelo catalogo de textos */
    public CampanhaInvalidaException(String codigo, String detalhe) {
        super(detalhe);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
