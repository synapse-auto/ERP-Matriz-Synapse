package com.synapse.crm.atendimento.domain.avaliacao;

import java.util.UUID;

/** O atendimento ja tem nota; uma segunda escrita nao sobrescreve. */
public class AvaliacaoJaRegistradaException extends RuntimeException {

    public AvaliacaoJaRegistradaException(UUID atendimentoId) {
        super("atendimento " + atendimentoId + " ja possui avaliacao");
    }
}
