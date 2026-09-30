package com.synapse.crm.atendimento.application;

/**
 * A chave de envio informada ao registrar a saida nao combina com a reserva: pertence a outro
 * atendimento ou ja foi concluida com outro wamid (sinal de um segundo envio que contornou a reserva).
 */
public class ReservaDeEnvioConflitanteException extends RuntimeException {

    public ReservaDeEnvioConflitanteException(String detalhe) {
        super(detalhe);
    }
}
