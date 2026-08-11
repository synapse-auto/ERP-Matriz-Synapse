package com.synapse.crm.relatorios.domain.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Variação calculada no backend, com unidade explícita para não confundir % com pp. */
public record Comparativo(BigDecimal valor, Unidade unidade) {

    public enum Unidade {
        PERCENTUAL,
        PONTOS_PERCENTUAIS,
        PONTOS
    }

    public Comparativo {
        valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static Comparativo percentual(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0 || atual == null) return null;
        BigDecimal variacao = atual.subtract(anterior)
                .divide(anterior, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return new Comparativo(variacao, Unidade.PERCENTUAL);
    }

    public static Comparativo pontosPercentuais(BigDecimal atual, BigDecimal anterior) {
        if (atual == null || anterior == null) return null;
        return new Comparativo(atual.subtract(anterior), Unidade.PONTOS_PERCENTUAIS);
    }

    public static Comparativo pontos(BigDecimal atual, BigDecimal anterior) {
        if (atual == null || anterior == null) return null;
        return new Comparativo(atual.subtract(anterior), Unidade.PONTOS);
    }
}
