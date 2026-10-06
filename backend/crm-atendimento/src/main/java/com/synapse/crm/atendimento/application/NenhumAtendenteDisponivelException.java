package com.synapse.crm.atendimento.application;

/**
 * Nao existe destino elegivel para a Automacao neste momento. A mensagem e o status 409 sao contrato com o n8n e nao
 * mudam; o {@link #motivo()} e o campo aditivo que diz qual filtro do rodizio zerou.
 */
public class NenhumAtendenteDisponivelException extends RuntimeException {

    private final MotivoSemAtendente motivo;

    public NenhumAtendenteDisponivelException(MotivoSemAtendente motivo) {
        super("nenhum atendente esta online e disponivel para receber a conversa");
        this.motivo = motivo;
    }

    public MotivoSemAtendente motivo() {
        return motivo;
    }
}
