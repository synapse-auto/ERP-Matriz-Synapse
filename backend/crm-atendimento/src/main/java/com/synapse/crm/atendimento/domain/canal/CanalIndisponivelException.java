package com.synapse.crm.atendimento.domain.canal;

/** O provedor recusou ou o circuit breaker esta aberto — nao ha lista nem criacao neste instante. */
public class CanalIndisponivelException extends RuntimeException {

    public CanalIndisponivelException(String motivo) {
        super(motivo);
    }
}
