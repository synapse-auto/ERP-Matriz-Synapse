package com.synapse.crm.atendimento.application.internal;

/** O inicio do recorte de atividade nao pode ser posterior ao fim. */
public class PeriodoDeAtividadeInvalidoException extends RuntimeException {

    public PeriodoDeAtividadeInvalidoException() {
        super("atividadeDesde deve ser anterior ou igual a atividadeAte");
    }
}
