package com.synapse.crm.relatorios.domain.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload único da aba Visão Geral, composto exclusivamente por read models. */
public record VisaoGeralDashboard(
        Periodo periodo,
        Atendimentos atendimentos,
        NovosLeads novosLeads,
        TempoMedioAtendimento tempoMedioAtendimento,
        AvaliacaoMedia avaliacaoMedia,
        ResolucaoPorIa resolucaoPorIa,
        VendasFechadas vendasFechadas,
        TaxaConversao taxaConversao,
        StatusAoVivo statusAoVivo,
        List<EtapaDoFunil> funil,
        long leadsPerdidos,
        List<MensagensPorHora> horarioDePico,
        RankingDeVendas rankingDeVendas,
        RankingDeAvaliacoes rankingDeAvaliacoes,
        List<AtendenteDesempenho> equipeDesempenho) {

    public VisaoGeralDashboard {
        funil = List.copyOf(funil);
        horarioDePico = List.copyOf(horarioDePico);
        equipeDesempenho = List.copyOf(equipeDesempenho);
    }

    public record Periodo(int ano, List<Integer> meses, LocalDate inicio, LocalDate fim) {
        public Periodo {
            meses = List.copyOf(meses);
        }
    }

    public record Atendimentos(long noPeriodo, long acumulado, Comparativo comparativo) {}

    public record NovosLeads(long noPeriodo, Comparativo comparativo) {}

    /**
     * Contadores ao vivo, sem recorte de período: refletem o estado do atendimento no instante da
     * consulta (emIa/emAtendimento) ou o dia corrente no fuso do tenant (leadsNovosHoje/vendasHoje).
     * Os demais itens do "AGORA" do mockup (aguardando 1ª resposta, esquecidos, atendentes online)
     * ficam de fora: não existe hoje critério nem instrumentação para eles — ver relatório da E197.
     */
    public record StatusAoVivo(long emIa, long emAtendimento, long leadsNovosHoje, long vendasHoje) {}

    public record TempoMedioAtendimento(Long segundos, Comparativo comparativo) {}

    public record AvaliacaoMedia(
            BigDecimal media, int escalaMaxima, long quantidade, Comparativo comparativo) {}

    public record ResolucaoPorIa(
            BigDecimal percentual,
            long resolvidosSemTransferencia,
            long atendimentosFinalizados,
            Comparativo comparativo) {}

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

    public record RankingDeAvaliacoes(List<AtendenteNaAvaliacao> atendentes) {
        public RankingDeAvaliacoes {
            atendentes = List.copyOf(atendentes);
        }
    }

    public record AtendenteNaAvaliacao(UUID id, String nome, BigDecimal media, long quantidade) {}

    /**
     * Linha da tabela "Equipe · desempenho". {@code nota}/{@code avaliacoes} vêm nulos/zerados
     * quando o atendente não recebeu avaliação no período — nunca inventados. Conversão e 1ª
     * resposta por atendente ficam fora (ver relatório da E197: a primeira exige decidir "aguardando
     * 1ª resposta"; a segunda esbarraria em misturar atribuição por evento, usada em vendas, com
     * atribuição pela coluna atual do lead, que são semânticas diferentes).
     */
    public record AtendenteDesempenho(
            UUID id, String nome, long atendimentos, long vendas, BigDecimal nota, long avaliacoes) {}
}
