package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.UUID;

/** Porta de saída para o webhook de solicitação de resumo do n8n. */
public interface ResumoIaAutomacaoGateway {

    boolean configurado();

    Resultado enviar(UUID solicitacaoId, UUID leadId, UUID atendimentoId, Instant solicitadoEm);

    enum Resultado {
        ACEITO,
        TENTAR_NOVAMENTE,
        RECUSADO
    }
}
