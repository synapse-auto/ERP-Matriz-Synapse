package com.synapse.crm.automacaoconfig.domain.telemetria;

/**
 * O que a Automacao pode reportar via {@code POST /internal/v1/eventos} (E07 §1).
 *
 * <p>Fechado de proposito: um tipo de evento novo exige decidir explicitamente o que ele soma em
 * {@code status_automacao_telemetria}, nao um contador generico que aceita qualquer string.
 */
public enum TipoEventoAutomacao {
    MENSAGEM_ENVIADA,
    CLIENTE_TRANSFERIDO
}
