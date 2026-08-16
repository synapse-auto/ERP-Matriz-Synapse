package com.synapse.crm.atendimento.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

/** Grava a intencao de repasse; nunca faz chamada de rede no request da Meta. */
@Service
public class AgendarRepasseWebhookAutomacaoUseCase {

    private final Outbox outbox;
    private final RepasseWebhookAutomacaoGateway automacao;

    public AgendarRepasseWebhookAutomacaoUseCase(
            Outbox outbox, RepasseWebhookAutomacaoGateway automacao) {
        this.outbox = outbox;
        this.automacao = automacao;
    }

    public void executar(String payloadCru, String assinatura, Instant recebidoEm) {
        if (automacao.configurado()) {
            outbox.enfileirarRepasseWebhook(payloadCru, assinatura, recebidoEm);
        }
    }
}
