package com.synapse.crm.relatorios.application.vendas;

import java.time.Instant;
import java.util.List;

import com.synapse.crm.relatorios.domain.IntervaloTemporal;
import com.synapse.crm.relatorios.domain.vendas.AgregacaoDeVendas;

/** Porta unica da definicao de venda fechada para Dashboard, Equipe e futuros relatorios. */
public interface AgregacaoDeVendasRepositorio {

    /** Lista vazia em {@code periodos} significa todo o historico disponivel. */
    AgregacaoDeVendas agregar(
            List<IntervaloTemporal> periodos, IntervaloTemporal periodoDeOriginacao);

    /**
     * Mesma definicao canonica de venda de {@link #agregar(List, IntervaloTemporal)}, sem a
     * quebra por responsavel quando o chamador precisa apenas do total.
     */
    long totalDeVendas(List<IntervaloTemporal> periodos, IntervaloTemporal periodoDeOriginacao);

    long contarAte(Instant fimExclusivo, IntervaloTemporal periodoDeOriginacao);
}
