package com.synapse.crm.equipe.application.chat;

/** A mensagem existe e é visível, mas não é conteúdo destinado a um cliente. */
public class MensagemDoChatNaoEncaminhavelException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Motivo {
        REMOVIDA,
        EVENTO_DE_SISTEMA,
        TIPO_NAO_SUPORTADO,
        SEM_CONTEUDO,
        MIDIA_INDISPONIVEL
    }

    private final Motivo motivo;

    public MensagemDoChatNaoEncaminhavelException(Motivo motivo) {
        super("Mensagem do chat interno nao pode ser encaminhada ao cliente: " + motivo);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
