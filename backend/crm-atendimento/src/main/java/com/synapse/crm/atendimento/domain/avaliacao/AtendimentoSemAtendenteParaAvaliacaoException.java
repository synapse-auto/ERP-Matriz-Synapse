package com.synapse.crm.atendimento.domain.avaliacao;

import java.util.UUID;

/** {@code avaliacao.atendente_id} e NOT NULL: conversa so da IA nao tem a quem atribuir a nota. */
public class AtendimentoSemAtendenteParaAvaliacaoException extends RuntimeException {

    public AtendimentoSemAtendenteParaAvaliacaoException(UUID atendimentoId) {
        super("atendimento " + atendimentoId + " nao tem atendente para receber a avaliacao");
    }
}
