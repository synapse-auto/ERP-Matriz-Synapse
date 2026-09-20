package com.synapse.crm.atendimento.application.participacao;

/** Recusa um convite que não pode ser criado para o estado atual da conversa. */
public class ConviteAtendimentoInvalidoException extends RuntimeException {
    public ConviteAtendimentoInvalidoException(String motivo) {
        super(motivo);
    }
}
