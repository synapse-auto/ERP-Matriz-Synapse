package com.synapse.crm.atendimento.application.painel;

import java.util.List;

/**
 * A lista simples do painel (E225): os cartoes (no maximo {@code teto}) e se havia mais do que o teto. O corte nunca e
 * silencioso: o controller devolve o cabecalho {@code X-Lista-Truncada} e o caso de uso grava um WARN.
 *
 * @param cartoes os primeiros cartoes, na ordem do painel; nunca mais que {@code teto}
 * @param truncada {@code true} se existiam mais cartoes alem dos devolvidos
 * @param teto o limite em vigor ({@code synapse.painel.listagem-maxima})
 */
public record ListaDoPainel(List<CartaoAtendimento> cartoes, boolean truncada, int teto) {

    public ListaDoPainel {
        cartoes = List.copyOf(cartoes);
    }
}
