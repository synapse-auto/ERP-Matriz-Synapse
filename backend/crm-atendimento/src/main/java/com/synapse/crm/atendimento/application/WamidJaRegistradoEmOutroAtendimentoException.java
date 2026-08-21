package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** O mesmo identificador da Meta não pode representar duas conversas. */
public class WamidJaRegistradoEmOutroAtendimentoException extends RuntimeException {

    public WamidJaRegistradoEmOutroAtendimentoException(String wamid, UUID atendimentoId) {
        super("wamid " + wamid + " ja foi registrado em outro atendimento; recebido para " + atendimentoId);
    }
}
