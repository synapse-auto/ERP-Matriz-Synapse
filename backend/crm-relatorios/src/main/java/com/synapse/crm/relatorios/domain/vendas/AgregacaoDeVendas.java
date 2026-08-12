package com.synapse.crm.relatorios.domain.vendas;

import java.util.List;
import java.util.UUID;

/** Mesma definicao de venda usada por todos os read models gerenciais. */
public record AgregacaoDeVendas(
        long total, long semResponsavel, List<VendasPorAtendente> porAtendente) {

    public AgregacaoDeVendas {
        porAtendente = List.copyOf(porAtendente);
    }

    public record VendasPorAtendente(UUID atendenteId, String atendenteNome, long vendas) {}
}
