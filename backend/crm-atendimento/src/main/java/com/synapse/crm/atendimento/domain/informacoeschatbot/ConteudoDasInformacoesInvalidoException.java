package com.synapse.crm.atendimento.domain.informacoeschatbot;

/** O texto entregue pela Automacao nao pode virar um card: vazio, grande demais ou com caractere nulo. */
public class ConteudoDasInformacoesInvalidoException extends RuntimeException {

    public ConteudoDasInformacoesInvalidoException(String motivo) {
        super(motivo);
    }
}
