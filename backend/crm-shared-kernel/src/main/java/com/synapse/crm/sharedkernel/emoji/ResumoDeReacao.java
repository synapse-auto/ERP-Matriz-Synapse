package com.synapse.crm.sharedkernel.emoji;

/** Agrupamento visivel na bolha: quantidade e se o usuario corrente reagiu com este emoji. */
public record ResumoDeReacao(String emoji, int quantidade, boolean reagi) {

    public ResumoDeReacao {
        if (emoji == null || emoji.isBlank()) {
            throw new IllegalArgumentException("resumo de reacao exige emoji");
        }
        if (quantidade < 1) {
            throw new IllegalArgumentException("quantidade de reacao deve ser positiva");
        }
    }
}
