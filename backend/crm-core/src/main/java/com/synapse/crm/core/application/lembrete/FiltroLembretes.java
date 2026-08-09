package com.synapse.crm.core.application.lembrete;

import java.time.Instant;
import java.util.UUID;

import com.synapse.crm.core.domain.lembrete.StatusLembrete;

/**
 * {@code leadId} (E17 §Bloco 2): filtro opcional para a secao "Lembretes" do painel de atendimento
 * — os lembretes de UM lead, nao um periodo. E filtro puro sobre a tabela existente, nao agregacao
 * nova; a visibilidade continua vindo do JOIN com o lead do usuario, nunca deste parametro sozinho.
 */
public record FiltroLembretes(
        Instant inicio, Instant fim, StatusLembrete status, UUID leadId, int pagina, int tamanho) {
    public FiltroLembretes {
        if (pagina < 0 || tamanho < 1) {
            throw new IllegalArgumentException("pagina e tamanho devem ser positivos");
        }
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new IllegalArgumentException("fim deve ser posterior ao inicio");
        }
    }
}
