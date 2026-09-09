package com.synapse.crm.atendimento.application.midia;

/** Falha ao normalizar uma gravação de áudio do composer para um formato aceito pelo canal. */
public class FalhaNaConversaoDeAudioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FalhaNaConversaoDeAudioException(String mensagem) {
        super(mensagem);
    }

    public FalhaNaConversaoDeAudioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
