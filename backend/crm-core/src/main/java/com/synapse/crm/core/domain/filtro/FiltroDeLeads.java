package com.synapse.crm.core.domain.filtro;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Arvore de criterios pronta para interpretar.
 *
 * <p>O tipo existe para que <strong>nao haja como passar adiante uma arvore nao verificada</strong>.
 * Os limites sao conferidos no construtor compacto, entao qualquer caminho de entrada — HTTP hoje,
 * fila ou job amanha — recebe a mesma protecao sem depender de alguem lembrar de chamar um validador.
 *
 * <p>A travessia e <em>iterativa</em>, de proposito. Uma verificacao recursiva de profundidade
 * estoura a propria pilha exatamente no caso que ela deveria barrar — a arvore funda demais — e troca
 * um 400 honesto por um 500.
 */
public record FiltroDeLeads(Criterio raiz) {

    /**
     * Quantos niveis de parenteses o filtro aceita.
     *
     * <p>Cinco cobre com folga o que uma tela de filtro consegue montar. O objetivo do limite nao e
     * expressividade, e custo: cada nivel multiplica o tamanho do {@code WHERE} e o tempo de
     * planejamento da consulta no Postgres.
     */
    public static final int PROFUNDIDADE_MAXIMA = 5;

    /** Teto de nos na arvore inteira, contando folhas e compostos. */
    public static final int NOS_MAXIMOS = 200;

    public FiltroDeLeads {
        Objects.requireNonNull(raiz, "um filtro precisa de um criterio raiz");
        verificarLimites(raiz);
    }

    /** Atalho para o caso mais comum: uma unica condicao, sem parenteses. */
    public static FiltroDeLeads de(Criterio criterio) {
        return new FiltroDeLeads(criterio);
    }

    /** Atalho para varias condicoes ligadas por E. */
    public static FiltroDeLeads todosOsCriterios(List<Criterio> criterios) {
        return new FiltroDeLeads(CriterioComposto.e(criterios));
    }

    /**
     * Percorre a arvore em largura contando nos e medindo profundidade.
     *
     * <p>Falha no primeiro estouro em vez de percorrer tudo antes de decidir: se a arvore veio grande
     * o bastante para ser um ataque, nao ha por que paga-la inteira para dizer nao.
     */
    private static void verificarLimites(Criterio raiz) {
        Deque<No> pendentes = new ArrayDeque<>();
        pendentes.add(new No(raiz, 1));
        int total = 0;

        while (!pendentes.isEmpty()) {
            No atual = pendentes.removeFirst();
            total++;

            if (atual.profundidade() > PROFUNDIDADE_MAXIMA) {
                throw new FiltroInvalidoException(
                        "filtro aninhado alem de " + PROFUNDIDADE_MAXIMA + " niveis");
            }
            if (total > NOS_MAXIMOS) {
                throw new FiltroInvalidoException("filtro com mais de " + NOS_MAXIMOS + " criterios");
            }

            if (atual.criterio() instanceof CriterioComposto composto) {
                for (Criterio filho : composto.criterios()) {
                    pendentes.add(new No(filho, atual.profundidade() + 1));
                }
            }
        }
    }

    private record No(Criterio criterio, int profundidade) {}
}
