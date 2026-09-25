package com.synapse.crm.relatorios.application.vendas;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.synapse.crm.relatorios.domain.IntervaloTemporal;
import com.synapse.crm.relatorios.domain.vendas.AgregacaoDeVendas;

/** Porta unica da definicao de venda fechada para Dashboard, Equipe e futuros relatorios. */
public interface AgregacaoDeVendasRepositorio {

    /** Lista vazia em {@code periodos} significa todo o historico disponivel. */
    AgregacaoDeVendas agregar(
            List<IntervaloTemporal> periodos, IntervaloTemporal periodoDeOriginacao);

    /** Dashboard: ranking e serie mensal extraidos da mesma leitura da timeline. */
    VendasComSerie agregarComSerie(
            List<IntervaloTemporal> periodos,
            IntervaloTemporal periodoDeOriginacao,
            ZoneId fusoHorario);

    /**
     * Mesma definicao canonica de venda de {@link #agregar(List, IntervaloTemporal)}, sem a
     * quebra por responsavel quando o chamador precisa apenas do total.
     */
    long totalDeVendas(List<IntervaloTemporal> periodos, IntervaloTemporal periodoDeOriginacao);

    long contarAte(Instant fimExclusivo, IntervaloTemporal periodoDeOriginacao);

    record VendasComSerie(AgregacaoDeVendas agregado, Map<YearMonth, Long> porMes) {
        public VendasComSerie {
            porMes = Map.copyOf(porMes);
        }
    }
}
