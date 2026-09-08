package com.synapse.crm.atendimento.application.midia;

/** Falha ao produzir o OGG/Opus exigido para uma gravação do composer. */
public class FalhaNaConversaoDeAudioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FalhaNaConversaoDeAudioException(String mensagem) {
        super(mensagem);
    }

    public FalhaNaConversaoDeAudioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
