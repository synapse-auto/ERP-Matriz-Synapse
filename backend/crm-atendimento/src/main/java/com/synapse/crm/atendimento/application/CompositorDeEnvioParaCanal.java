package com.synapse.crm.atendimento.application;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;

/** Monta o envio externo a partir da mensagem persistida e do payload da outbox. */
@FunctionalInterface
public interface CompositorDeEnvioParaCanal {

    CanalGateway.Envio montar(Outbox.EnvioPendente pendente);
}
