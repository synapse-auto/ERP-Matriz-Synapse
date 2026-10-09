package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Aviso de que um card de informacoes do chatbot entrou no historico do atendimento.
 *
 * <p>Leve de proposito: nao carrega o texto. Quem recebe revalida pela leitura HTTP autorizada, assim
 * o conteudo nunca trafega pelo backplane nem depende da filtragem de quem esta assinado.
 */
public record InformacoesDoChatbotParaTempoReal(
        UUID atendimentoId, UUID leadId, UUID informacaoId, Instant ocorridoEm) {}
