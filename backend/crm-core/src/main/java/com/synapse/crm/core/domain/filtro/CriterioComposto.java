package com.synapse.crm.core.domain.filtro;

import java.util.List;
import java.util.Objects;

/**
 * No interno da arvore: um conector e os filhos que ele liga.
 *
 * <p>E o que torna o filtro modular de verdade — {@code (etapa = X OU tag = Y) E semRetornoDias > 30}
 * e um composto {@code E} com um composto {@code OU} dentro.
 *
 * <p>Lista vazia e recusada. Um {@code E} sem filhos nao tem traducao honesta: viraria "verdadeiro"
 * (filtro que nao filtra) ou "falso" (tela sempre vazia), e as duas escolhas surpreendem alguem.
 * Melhor a requisicao falhar dizendo o que faltou.
 *
 * @param conector como os filhos se combinam
 * @param criterios filhos, ao menos um
 */
public record CriterioComposto(Conector conector, List<Criterio> criterios) implements Criterio {

    /** Quantos filhos um no aceita. Corta a arvore larga, como o limite de profundidade corta a funda. */
    public static final int MAXIMO_DE_FILHOS = 50;

    public CriterioComposto {
        Objects.requireNonNull(conector, "conector e obrigatorio");
        criterios = criterios == null ? List.of() : List.copyOf(criterios);

        if (criterios.isEmpty()) {
            throw new FiltroInvalidoException(
                    "um criterio " + conector + " precisa de ao menos um filho");
        }
        if (criterios.size() > MAXIMO_DE_FILHOS) {
            throw new FiltroInvalidoException("um criterio " + conector + " aceita no maximo "
                    + MAXIMO_DE_FILHOS + " filhos, e recebeu " + criterios.size());
        }
    }

    public static CriterioComposto e(List<Criterio> criterios) {
        return new CriterioComposto(Conector.E, criterios);
    }

    public static CriterioComposto ou(List<Criterio> criterios) {
        return new CriterioComposto(Conector.OU, criterios);
    }

    /**
     * Como os filhos se combinam.
     *
     * <p>Em portugues como o resto do dominio (CLAUDE.md). O JSON escreve {@code "E"} ou {@code "OU"}.
     */
    public enum Conector {
        E,
        OU;

        public static Conector de(String recebido) {
            if (recebido != null) {
                String limpo = recebido.trim().toUpperCase();
                for (Conector conector : values()) {
                    if (conector.name().equals(limpo)) {
                        return conector;
                    }
                }
            }
            throw new FiltroInvalidoException("conector nao permitido: "
                    + FiltroInvalidoException.eco(recebido) + ". Permitidos: [E, OU]");
        }
    }
}
