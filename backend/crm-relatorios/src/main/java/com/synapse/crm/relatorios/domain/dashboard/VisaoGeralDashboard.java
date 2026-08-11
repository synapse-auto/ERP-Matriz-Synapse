package com.synapse.crm.relatorios.domain.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload único da aba Visão Geral, composto exclusivamente por read models. */
public record VisaoGeralDashboard(
        Periodo periodo,
        Atendimentos atendimentos,
        TempoMedioAtendimento tempoMedioAtendimento,
        AvaliacaoMedia avaliacaoMedia,
        VendasFechadas vendasFechadas,
        TaxaConversao taxaConversao,
        List<EtapaDoFunil> funil,
        List<MensagensPorHora> horarioDePico,
        RankingDeVendas rankingDeVendas) {

    public VisaoGeralDashboard {
        funil = List.copyOf(funil);
        horarioDePico = List.copyOf(horarioDePico);
    }

    public record Periodo(int ano, List<Integer> meses) {
        public Periodo {
            meses = List.copyOf(meses);
        }
    }

    public record Atendimentos(long noPeriodo, long acumulado, Comparativo comparativo) {}

    public record TempoMedioAtendimento(Long segundos, Comparativo comparativo) {}

    public record AvaliacaoMedia(
            BigDecimal media, int escalaMaxima, long quantidade, Comparativo comparativo) {}

    public record VendasFechadas(long noPeriodo, long acumulado, Comparativo comparativo) {}

    public record TaxaConversao(
            BigDecimal percentual, long vendas, long leadsRecebidos, Comparativo comparativo) {}

    public record EtapaDoFunil(
            UUID id,
            String nome,
            int ordem,
            String corVisual,
            long quantidade,
            BigDecimal percentualDePassagem) {}

    public record MensagensPorHora(int hora, long quantidade) {}

    public record RankingDeVendas(List<AtendenteNoRanking> atendentes, long semResponsavel) {
        public RankingDeVendas {
            atendentes = List.copyOf(atendentes);
        }
    }

    public record AtendenteNoRanking(UUID id, String nome, long vendas) {}
}
