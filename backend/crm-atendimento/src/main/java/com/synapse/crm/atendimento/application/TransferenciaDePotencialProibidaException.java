package com.synapse.crm.atendimento.application;

/**
 * Atendente tentou entregar um Potencial ({@code EM_IA}) a um colega. A RLS deixa o grupo sem
 * dono visível a todos; sem esta recusa, a escolha a dedo contornaria a RN-CRM-06.
 */
public class TransferenciaDePotencialProibidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TransferenciaDePotencialProibidaException() {
        super("atendente nao pode escolher o destino de um potencial");
    }
}
