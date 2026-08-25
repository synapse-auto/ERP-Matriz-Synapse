package com.synapse.crm.atendimento.application.internal;

import java.util.UUID;

/** Lembrete pessoal exige uma pessoa responsavel no instante da chamada. */
public class AtendimentoSemResponsavelException extends RuntimeException {

    public AtendimentoSemResponsavelException(UUID atendimentoId) {
        super("Atendimento " + atendimentoId + " nao possui atendente responsavel");
    }
}
