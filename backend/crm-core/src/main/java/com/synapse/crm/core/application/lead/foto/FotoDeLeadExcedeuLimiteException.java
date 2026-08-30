package com.synapse.crm.core.application.lead.foto;

/** Arquivo acima do limite configurado em {@code configuracao_automacao}. Vira 413. */
public class FotoDeLeadExcedeuLimiteException extends RuntimeException {

    public FotoDeLeadExcedeuLimiteException(long limiteEmBytes) {
        super("a foto excede o limite de " + limiteEmBytes + " bytes");
    }
}
