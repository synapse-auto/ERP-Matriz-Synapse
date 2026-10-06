package com.synapse.crm.atendimento.application.encaminhamentodochat;

/** A mensagem do chat é válida internamente, mas o envio ao cliente a recusa. */
public class ConteudoDoChatNaoEnviavelAoClienteException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final MotivoDeBloqueio motivo;

    public ConteudoDoChatNaoEnviavelAoClienteException(MotivoDeBloqueio motivo, String detalhe) {
        super(detalhe);
        this.motivo = motivo;
    }

    public MotivoDeBloqueio motivo() {
        return motivo;
    }
}
