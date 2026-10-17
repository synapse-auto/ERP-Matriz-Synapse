package com.synapse.crm.atendimento.application;

/** Recusa permanente antes da chamada ao canal; nunca repetir envio de uma conta de teste. */
public class ContaDeTesteSomenteLeituraException extends RuntimeException {
    public ContaDeTesteSomenteLeituraException() { super("CONTA_TESTE_SOMENTE_LEITURA"); }
}
