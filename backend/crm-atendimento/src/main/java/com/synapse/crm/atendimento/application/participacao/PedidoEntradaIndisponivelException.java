package com.synapse.crm.atendimento.application.participacao;

/** O pedido ou convite expirou, já foi respondido, ou o atendimento não aceita mais resposta. */
public class PedidoEntradaIndisponivelException extends RuntimeException {
    public PedidoEntradaIndisponivelException(String motivo) {
        super(motivo);
    }
}
