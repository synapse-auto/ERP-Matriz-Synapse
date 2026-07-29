package com.synapse.crm.automacaoconfig.application.telemetria;

import com.synapse.crm.automacaoconfig.domain.telemetria.TipoEventoAutomacao;

/**
 * Porta de escrita de {@code status_automacao_telemetria} — singleton (uma linha, {@code id = 1}).
 *
 * <p>So incrementa: cada evento reportado soma no contador do seu tipo e marca a conexao como ativa
 * e o instante como agora. Nao ha leitura aqui porque {@code POST /internal/v1/eventos} nao devolve
 * o estado acumulado, so confirma o registro.
 */
public interface StatusAutomacaoTelemetriaRepositorio {

    void registrar(TipoEventoAutomacao tipo);
}
