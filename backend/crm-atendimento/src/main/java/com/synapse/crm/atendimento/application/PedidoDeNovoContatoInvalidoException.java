package com.synapse.crm.atendimento.application;

/** Nome ausente, telefone ilegivel, ou texto livre misturado com template. */
public class PedidoDeNovoContatoInvalidoException extends RuntimeException {

    public PedidoDeNovoContatoInvalidoException(String motivo) {
        super(motivo);
    }
}
