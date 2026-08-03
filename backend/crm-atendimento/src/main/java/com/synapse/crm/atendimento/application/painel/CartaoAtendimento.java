package com.synapse.crm.atendimento.application.painel;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;

/**
 * Leitura para o card da lista de conversas — nunca escrita. Campos de etapa/atendente/ultima
 * mensagem sao nulos quando nao ha o dado (lead sem etapa, atendimento sem dono, atendimento sem
 * mensagem ainda).
 *
 * @param ultimaMensagemDoLeadEm instante da ultima mensagem com {@code remetenteTipo = LEAD} — usado
 *     pelo frontend para estimar a janela de 24h antes de o atendente digitar; a autoridade real
 *     continua sendo a checagem no {@code EnviarMensagemUseCase}.
 */
public record CartaoAtendimento(
        UUID atendimentoId,
        UUID leadId,
        String leadNome,
        String leadFotoUrl,
        String leadEmpresa,
        UUID etapaId,
        String etapaNome,
        String etapaCor,
        StatusAtendimento status,
        UUID atendenteId,
        String atendenteNome,
        String ultimaMensagemPreview,
        String ultimaMensagemRemetenteTipo,
        Instant ultimaMensagemEm,
        Instant ultimaMensagemDoLeadEm) {}
