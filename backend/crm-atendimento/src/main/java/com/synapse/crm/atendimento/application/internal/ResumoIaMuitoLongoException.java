package com.synapse.crm.atendimento.application.internal;

/** Resumo maior que o limite operacional configurado para a instancia. */
public class ResumoIaMuitoLongoException extends RuntimeException {

    public ResumoIaMuitoLongoException(int tamanhoMaximo) {
        super("Resumo da IA excede o limite de " + tamanhoMaximo + " caracteres");
    }
}
