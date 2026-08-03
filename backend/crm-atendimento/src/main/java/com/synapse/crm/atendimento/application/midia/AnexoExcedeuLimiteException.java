package com.synapse.crm.atendimento.application.midia;

/** Tamanho acima do limite configurado (ou do teto da Meta, se nada foi configurado). */
public class AnexoExcedeuLimiteException extends RuntimeException {

    public AnexoExcedeuLimiteException(long tamanhoBytes, long limiteBytes) {
        super("anexo de " + tamanhoBytes + " bytes excede o limite de " + limiteBytes + " bytes");
    }
}
