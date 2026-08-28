package com.synapse.crm.relatorios.infrastructure.persistencia.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.relatorios.application.dashboard.DashboardVisaoGeralRepositorio;
import com.synapse.crm.relatorios.application.vendas.AgregacaoDeVendasRepositorio;
import com.synapse.crm.relatorios.domain.IntervaloTemporal;
import com.synapse.crm.relatorios.domain.dashboard.Comparativo;
import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;
import com.synapse.crm.relatorios.domain.vendas.AgregacaoDeVendas;

/** Read model SQL consolidado; nenhuma consulta participa do caminho crítico de mensagens. */
@Repository
class DashboardVisaoGeralRepositorioJdbc implements DashboardVisaoGeralRepositorio {

    private final JdbcTemplate jdbc;
    private final AgregacaoDeVendasRepositorio vendas;

    DashboardVisaoGeralRepositorioJdbc(
            JdbcTemplate jdbc, AgregacaoDeVendasRepositorio vendas) {
        this.jdbc = jdbc;
        this.vendas = vendas;
    }

    @Override
    public VisaoGeralDashboard consultar(FiltroTemporalDashboard filtro) {
        AgregadoAtendimento atual = atendimentos(filtro.periodoAtual());
        AgregadoAtendimento anterior = atendimentos(List.of(filtro.periodoAnterior()));
        long atendimentosAcumulados = contarAte("atendimento", "iniciado_em", filtro);

        AgregadoAvaliacao avaliacaoAtual = avaliacoes(filtro.periodoAtual());
        AgregadoAvaliacao avaliacaoAnterior = avaliacoes(List.of(filtro.periodoAnterior()));

        AgregadoResolucaoIa resolucaoIaAtual = resolucaoPorIa(filtro.periodoAtual());
        AgregadoResolucaoIa resolucaoIaAnterior =
                resolucaoPorIa(List.of(filtro.periodoAnterior()));
        BigDecimal taxaResolucaoIaAtual = percentual(
                resolucaoIaAtual.semTransferencia(), resolucaoIaAtual.finalizados());
        BigDecimal taxaResolucaoIaAnterior = percentual(
                resolucaoIaAnterior.semTransferencia(), resolucaoIaAnterior.finalizados());

        AgregacaoDeVendas vendasAtual =
                vendas.agregar(filtro.periodoAtual(), filtro.periodoDeOriginacao());
        AgregacaoDeVendas vendasAnterior =
                vendas.agregar(List.of(filtro.periodoAnterior()), filtro.periodoDeOriginacao());
        long vendasAcumuladas =
                vendas.contarAte(filtro.fimDoPeriodoAtual(), filtro.periodoDeOriginacao());

        long leadsAtuais = contarLeadsDaConversao(filtro, true);
        long leadsAnteriores = contarLeadsDaConversao(filtro, false);
        BigDecimal taxaAtual = percentual(vendasAtual.total(), leadsAtuais);
        BigDecimal taxaAnterior = percentual(vendasAnterior.total(), leadsAnteriores);

        return new VisaoGeralDashboard(
                new VisaoGeralDashboard.Periodo(
                        filtro.ano(), filtro.meses(), filtro.recorteInicio(), filtro.recorteFim()),
                new VisaoGeralDashboard.Atendimentos(
                        atual.quantidade(),
                        atendimentosAcumulados,
                        Comparativo.percentual(decimal(atual.quantidade()), decimal(anterior.quantidade()))),
                new VisaoGeralDashboard.TempoMedioAtendimento(
                        segundos(atual.mediaSegundos()),
                        Comparativo.percentual(atual.mediaSegundos(), anterior.mediaSegundos())),
                new VisaoGeralDashboard.AvaliacaoMedia(
                        escala(avaliacaoAtual.media()),
                        5,
                        avaliacaoAtual.quantidade(),
                        Comparativo.pontos(avaliacaoAtual.media(), avaliacaoAnterior.media())),
                new VisaoGeralDashboard.ResolucaoPorIa(
                        taxaResolucaoIaAtual,
                        resolucaoIaAtual.semTransferencia(),
                        resolucaoIaAtual.finalizados(),
                        Comparativo.pontosPercentuais(
                                taxaResolucaoIaAtual, taxaResolucaoIaAnterior)),
                new VisaoGeralDashboard.VendasFechadas(
                        vendasAtual.total(),
                        vendasAcumuladas,
                        Comparativo.percentual(
                                decimal(vendasAtual.total()), decimal(vendasAnterior.total()))),
                new VisaoGeralDashboard.TaxaConversao(
                        taxaAtual,
                        vendasAtual.total(),
                        leadsAtuais,
                        Comparativo.pontosPercentuais(taxaAtual, taxaAnterior)),
                funil(filtro),
                mensagensPorHora(filtro),
                new VisaoGeralDashboard.RankingDeVendas(
                        vendasAtual.porAtendente().stream()
                                .map(item -> new VisaoGeralDashboard.AtendenteNoRanking(
                                        item.atendenteId(), item.atendenteNome(), item.vendas()))
                                .toList(),
                        vendasAtual.semResponsavel()),
                new VisaoGeralDashboard.RankingDeAvaliacoes(rankingAvaliacoes(filtro.periodoAtual())));
    }

    private AgregadoAtendimento atendimentos(List<IntervaloTemporal> periodos) {
        FiltroSql filtro = periodos("iniciado_em", periodos);
        return jdbc.queryForObject(
                """
                SELECT count(*) AS quantidade,
                       avg(extract(epoch FROM (finalizado_em - iniciado_em)))
                           FILTER (WHERE finalizado_em IS NOT NULL) AS media_segundos
                  FROM atendimento
                 WHERE %s
                """.formatted(filtro.clausula()),
                (linha, indice) -> new AgregadoAtendimento(
                        linha.getLong("quantidade"), linha.getBigDecimal("media_segundos")),
                filtro.parametros().toArray());
    }

    private AgregadoAvaliacao avaliacoes(List<IntervaloTemporal> periodos) {
        FiltroSql filtro = periodos("criado_em", periodos);
        return jdbc.queryForObject(
                "SELECT count(*) AS quantidade, avg(nota) AS media FROM avaliacao WHERE "
                        + filtro.clausula(),
                (linha, indice) -> new AgregadoAvaliacao(
                        linha.getLong("quantidade"), linha.getBigDecimal("media")),
                filtro.parametros().toArray());
    }

    private List<VisaoGeralDashboard.AtendenteNaAvaliacao> rankingAvaliacoes(
            List<IntervaloTemporal> periodos) {
        FiltroSql filtro = periodos("a.criado_em", periodos);
        return jdbc.query(
                """
                SELECT u.id, u.nome, round(avg(a.nota), 2) AS media, count(*) AS quantidade
                  FROM avaliacao a
                  JOIN usuario u ON u.id = a.atendente_id
                 WHERE %s
                 GROUP BY u.id, u.nome
                 ORDER BY media DESC, quantidade DESC, u.nome
                """.formatted(filtro.clausula()),
                (linha, indice) -> new VisaoGeralDashboard.AtendenteNaAvaliacao(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getBigDecimal("media"),
                        linha.getLong("quantidade")),
                filtro.parametros().toArray());
    }

    /**
     * Os dois tipos abaixo cobrem os tres caminhos de entrega humana: assumir por envio gera
     * LEAD_TRANSFERIDO_POR_ENVIO; transferencia manual, reatribuicao por gestor e Automacao geram
     * ATENDIMENTO_TRANSFERIDO. A ausencia e avaliada no historico inteiro do atendimento, enquanto
     * o denominador e recortado por finalizado_em.
     */
    private AgregadoResolucaoIa resolucaoPorIa(List<IntervaloTemporal> periodos) {
        FiltroSql filtro = periodos("a.finalizado_em", periodos);
        return jdbc.queryForObject(
                """
                SELECT count(*) AS finalizados,
                       count(*) FILTER (
                           WHERE NOT EXISTS (
                               SELECT 1
                                 FROM evento_timeline evento
                                WHERE evento.atendimento_id = a.id
                                  AND evento.tipo IN (
                                      'LEAD_TRANSFERIDO_POR_ENVIO',
                                      'ATENDIMENTO_TRANSFERIDO'
                                  )
                           )
                       ) AS sem_transferencia
                  FROM atendimento a
                 WHERE %s
                """.formatted(filtro.clausula()),
                (linha, indice) -> new AgregadoResolucaoIa(
                        linha.getLong("finalizados"), linha.getLong("sem_transferencia")),
                filtro.parametros().toArray());
    }

    private long contarLeadsDaConversao(FiltroTemporalDashboard filtro, boolean atual) {
        if (filtro.periodoDeOriginacao() != null) {
            return contarLeads(List.of(filtro.periodoDeOriginacao()));
        }
        return contarLeads(atual ? filtro.periodoAtual() : List.of(filtro.periodoAnterior()));
    }

    private long contarLeads(List<IntervaloTemporal> periodos) {
        FiltroSql temporal = periodos("criado_em", periodos);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM lead WHERE " + temporal.clausula(),
                Long.class,
                temporal.parametros().toArray());
        return total == null ? 0 : total;
    }

    private List<VisaoGeralDashboard.EtapaDoFunil> funil(FiltroTemporalDashboard filtro) {
        FiltroSql coorte = filtro.periodoDeOriginacao() == null
                ? periodos("l.criado_em", filtro.periodoAtual())
                : intervalo("l.criado_em", filtro.periodoDeOriginacao());
        List<EtapaBruta> etapas = jdbc.query(
                """
                SELECT e.id, e.nome, e.ordem, e.cor_visual, count(l.id) AS quantidade
                  FROM etapa_atendimento e
                  LEFT JOIN lead l ON l.etapa_atendimento_id = e.id AND %s
                 GROUP BY e.id, e.nome, e.ordem, e.cor_visual
                 ORDER BY e.ordem, e.nome
                """.formatted(coorte.clausula()),
                (linha, indice) -> new EtapaBruta(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getInt("ordem"),
                        linha.getString("cor_visual"),
                        linha.getLong("quantidade")),
                coorte.parametros().toArray());
        List<VisaoGeralDashboard.EtapaDoFunil> resposta = new ArrayList<>();
        long anterior = 0;
        for (int indice = 0; indice < etapas.size(); indice++) {
            EtapaBruta etapa = etapas.get(indice);
            BigDecimal passagem = indice == 0 || anterior == 0
                    ? null
                    : percentual(etapa.quantidade(), anterior);
            resposta.add(new VisaoGeralDashboard.EtapaDoFunil(
                    etapa.id(),
                    etapa.nome(),
                    etapa.ordem(),
                    etapa.corVisual(),
                    etapa.quantidade(),
                    passagem));
            anterior = etapa.quantidade();
        }
        return resposta;
    }

    private List<VisaoGeralDashboard.MensagensPorHora> mensagensPorHora(
            FiltroTemporalDashboard filtro) {
        FiltroSql temporal = periodos("enviado_em", filtro.periodoAtual());
        List<Object> parametros = new ArrayList<>();
        parametros.add(filtro.fusoHorario().getId());
        parametros.addAll(temporal.parametros());
        Map<Integer, Long> porHora = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT extract(hour FROM enviado_em AT TIME ZONE ?)::int AS hora,
                       count(*) AS quantidade
                  FROM mensagem
                 WHERE %s
                 GROUP BY hora
                 ORDER BY hora
                """.formatted(temporal.clausula()),
                (linha, indice) -> {
                    porHora.put(linha.getInt("hora"), linha.getLong("quantidade"));
                    return null;
                },
                parametros.toArray());
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(hora -> new VisaoGeralDashboard.MensagensPorHora(
                        hora, porHora.getOrDefault(hora, 0L)))
                .toList();
    }

    private long contarAte(String tabela, String coluna, FiltroTemporalDashboard filtro) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE " + coluna + " < ?",
                Long.class,
                Timestamp.from(filtro.fimDoPeriodoAtual()));
        return total == null ? 0 : total;
    }

    private static FiltroSql periodos(String coluna, List<IntervaloTemporal> periodos) {
        List<String> partes = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        for (IntervaloTemporal periodo : periodos) {
            partes.add("(" + coluna + " >= ? AND " + coluna + " < ?)");
            parametros.add(Timestamp.from(periodo.inicioInclusivo()));
            parametros.add(Timestamp.from(periodo.fimExclusivo()));
        }
        return new FiltroSql("(" + String.join(" OR ", partes) + ")", parametros);
    }

    private static FiltroSql intervalo(String coluna, IntervaloTemporal intervalo) {
        return new FiltroSql(
                "(" + coluna + " >= ? AND " + coluna + " < ?)",
                List.of(
                        Timestamp.from(intervalo.inicioInclusivo()),
                        Timestamp.from(intervalo.fimExclusivo())));
    }

    private static BigDecimal percentual(long parte, long total) {
        return total == 0 ? null : BigDecimal.valueOf(parte)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(long valor) {
        return BigDecimal.valueOf(valor);
    }

    private static Long segundos(BigDecimal valor) {
        return valor == null ? null : valor.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private record FiltroSql(String clausula, List<Object> parametros) {
        static FiltroSql vazio() {
            return new FiltroSql("", List.of());
        }

        boolean vazia() {
            return clausula.isEmpty();
        }
    }

    private record AgregadoAtendimento(long quantidade, BigDecimal mediaSegundos) {}

    private record AgregadoAvaliacao(long quantidade, BigDecimal media) {}

    private record AgregadoResolucaoIa(long finalizados, long semTransferencia) {}

    private record EtapaBruta(UUID id, String nome, int ordem, String corVisual, long quantidade) {}
}
