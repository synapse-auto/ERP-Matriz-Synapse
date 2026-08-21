package com.synapse.crm.atendimento.application;

/** Reserva global do identificador de uma mensagem recebida do provedor. */
public interface IdempotenciaDeMensagemRecebidaRepositorio {

    /** @return {@code true} somente quando o identificador ainda não havia sido processado. */
    boolean reservarSeNova(String wamid);
}
