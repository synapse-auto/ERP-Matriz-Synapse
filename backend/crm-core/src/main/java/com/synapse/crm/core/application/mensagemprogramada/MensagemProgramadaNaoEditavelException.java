package com.synapse.crm.core.application.mensagemprogramada;

public class MensagemProgramadaNaoEditavelException extends RuntimeException {
    public MensagemProgramadaNaoEditavelException() {
        super("Somente mensagens agendadas podem ser editadas ou canceladas");
    }
}
