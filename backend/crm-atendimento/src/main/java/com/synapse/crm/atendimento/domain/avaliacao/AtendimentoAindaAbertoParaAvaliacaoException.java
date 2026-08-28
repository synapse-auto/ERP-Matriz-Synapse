package com.synapse.crm.atendimento.domain.avaliacao;

import java.util.UUID;

/** CSAT so depois do encerramento — nota em conversa aberta distorce o periodo. */
public class AtendimentoAindaAbertoParaAvaliacaoException extends RuntimeException {

    public AtendimentoAindaAbertoParaAvaliacaoException(UUID atendimentoId) {
        super("atendimento " + atendimentoId + " ainda esta aberto e nao aceita avaliacao");
    }
}
