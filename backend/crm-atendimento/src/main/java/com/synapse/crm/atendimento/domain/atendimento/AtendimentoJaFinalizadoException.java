package com.synapse.crm.atendimento.domain.atendimento;

import java.util.UUID;

/**
 * Tentativa de mexer num atendimento encerrado.
 *
 * <p>{@code FINALIZADO} e estado terminal. Deixar transferir ou finalizar de novo produziria um
 * segundo evento de finalizacao — e a timeline do lead passaria a contar uma historia que nao
 * aconteceu.
 */
public class AtendimentoJaFinalizadoException extends RuntimeException {

    public AtendimentoJaFinalizadoException(UUID atendimentoId, String tentativa) {
        super("atendimento " + atendimentoId + " ja esta finalizado e nao aceita: " + tentativa);
    }
}
