package com.synapse.crm.automacaoconfig.domain.telemetria;

import java.time.Instant;

/**
 * Snapshot mais recente de {@code status_automacao_telemetria} (E17b §Bloco 6) — os quatro cards do
 * topo da tela de Automação: mensagens enviadas, clientes transferidos, se a Automação está
 * conectada e se o CRM está online.
 */
public record StatusAutomacaoTelemetria(
        long mensagensEnviadas,
        long clientesTransferidos,
        boolean conexaoAutomacaoAtiva,
        boolean crmOnline,
        Instant atualizadoEm) {}
