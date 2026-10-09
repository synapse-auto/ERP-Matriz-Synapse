package com.synapse.crm.atendimento.application.informacoeschatbot;

import java.util.UUID;

/**
 * O atendimento identificado nao esta no estado em que o card faz sentido.
 *
 * <p>O card descreve uma transferencia a humano, entao so entra em atendimento {@code EM_ATENDIMENTO}.
 * Um callback atrasado para atendimento ja encerrado e recusado em vez de gravado: o destino e o id
 * informado, nunca "o atendimento atual do lead", e um id que ficou para tras nao tem para onde ir.
 */
public class InformacoesDoChatbotRecusadasException extends RuntimeException {

    public enum Motivo {
        /** Atendimento ainda com a IA: a transferencia nao aconteceu (ou foi desfeita). */
        ATENDIMENTO_NAO_TRANSFERIDO,
        /** Estado terminal: o callback chegou depois do fim do atendimento. */
        ATENDIMENTO_FINALIZADO
    }

    private final Motivo motivo;

    public InformacoesDoChatbotRecusadasException(UUID atendimentoId, Motivo motivo) {
        super("atendimento " + atendimentoId + " nao aceita informacoes do chatbot: " + motivo);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
