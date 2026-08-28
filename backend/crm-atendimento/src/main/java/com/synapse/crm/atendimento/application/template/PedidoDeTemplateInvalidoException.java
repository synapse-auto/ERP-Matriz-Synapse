package com.synapse.crm.atendimento.application.template;

public class PedidoDeTemplateInvalidoException extends RuntimeException {

    public PedidoDeTemplateInvalidoException(String motivo) {
        super(motivo);
    }
}
