package com.synapse.crm.automacaoconfig.domain;

import java.util.Objects;

public record ConfiguracaoResumoIa(boolean ativo, GatilhoResumo gatilho, Integer quantidadeMensagens) {
    public ConfiguracaoResumoIa {
        Objects.requireNonNull(gatilho, "gatilho e obrigatorio");
        if ((gatilho == GatilhoResumo.A_CADA_X_MENSAGENS || gatilho == GatilhoResumo.AMBOS)
                && (quantidadeMensagens == null || quantidadeMensagens < 1)) {
            throw new IllegalArgumentException("quantidadeMensagens deve ser positiva para este gatilho");
        }
    }
}
