package com.synapse.crm.atendimento.application;

/** Porta HTTP de saida para entregar à Automacao exatamente o webhook recebido do canal. */
public interface RepasseWebhookAutomacaoGateway {

    boolean configurado();

    ResultadoRepasse repassar(String payloadCru, String assinatura);

    enum ResultadoRepasse {
        ACEITO,
        TENTAR_NOVAMENTE
    }
}
