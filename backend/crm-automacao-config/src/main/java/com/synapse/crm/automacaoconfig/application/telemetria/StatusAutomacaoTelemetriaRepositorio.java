package com.synapse.crm.automacaoconfig.application.telemetria;

import com.synapse.crm.automacaoconfig.domain.telemetria.StatusAutomacaoTelemetria;
import com.synapse.crm.automacaoconfig.domain.telemetria.TipoEventoAutomacao;

/**
 * Porta de {@code status_automacao_telemetria} — singleton (uma linha, {@code id = 1}).
 *
 * <p>{@link #registrar} so incrementa: cada evento reportado soma no contador do seu tipo e marca a
 * conexao como ativa e o instante como agora — {@code POST /internal/v1/eventos} nao devolve o
 * estado acumulado, so confirma o registro. {@link #obter} e a leitura para os quatro cards do topo
 * da tela de Automação (E17b §Bloco 6).
 */
public interface StatusAutomacaoTelemetriaRepositorio {

    void registrar(TipoEventoAutomacao tipo);

    StatusAutomacaoTelemetria obter();
}
