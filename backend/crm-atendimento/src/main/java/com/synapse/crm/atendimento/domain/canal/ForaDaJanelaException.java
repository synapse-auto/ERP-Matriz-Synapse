package com.synapse.crm.atendimento.domain.canal;

import java.util.UUID;

/**
 * Texto livre para um lead cuja janela de 24h ja fechou.
 *
 * <p>Falha <b>antes</b> de chegar ao provedor, de proposito. Deixar a Meta recusar significaria: uma
 * chamada de rede gasta, um 400 cru para traduzir, uma linha na outbox que vai esgotar, e um
 * atendente olhando "erro de envio" sem entender que precisava de um template. Aqui a mensagem diz o
 * que fazer.
 */
public class ForaDaJanelaException extends RuntimeException {

    public ForaDaJanelaException(UUID leadId) {
        super("a janela de 24h do lead " + leadId + " esta fechada: fora dela o provedor oficial so"
                + " aceita template pre-aprovado, nao texto livre");
    }
}
