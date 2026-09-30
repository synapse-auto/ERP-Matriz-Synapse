package com.synapse.crm.atendimento.application;

import java.util.UUID;

/** Porta HTTP de saida para entregar à Automacao exatamente o webhook recebido do canal. */
public interface RepasseWebhookAutomacaoGateway {

    boolean configurado();

    ResultadoRepasse repassar(Repasse repasse);

    /**
     * Uma entrega do webhook cru. {@code eventoId} e o id da linha da outbox: estavel entre as
     * retentativas do mesmo evento (e entre reentregas do mesmo POST do provedor, que caem na mesma
     * linha), e por isso a chave de deduplicacao do consumidor. {@code tentativa} comeca em 1.
     */
    record Repasse(UUID eventoId, int tentativa, String payloadCru, String assinatura) {}

    enum ResultadoRepasse {
        ACEITO,
        /** O destino nao recebeu (conexao recusada, 4xx/5xx, circuito aberto): reentregar e seguro. */
        TENTAR_NOVAMENTE,
        /**
         * O destino recebeu o corpo mas nao respondeu a tempo (timeout de leitura): pode ter
         * processado. A reentrega continua necessaria para nao perder o evento, e so e segura porque
         * leva o mesmo {@code eventoId} para o consumidor deduplicar.
         */
        INCERTO
    }
}
