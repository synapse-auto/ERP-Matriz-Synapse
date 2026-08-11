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
import com.synapse.crm.relatorios.domain.dashboard.Comparativo;
import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard.Intervalo;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;

/** Read model SQL consolidado; nenhuma consulta participa do caminho crítico de mensagens. */
@Repository
class DashboardVisaoGeralRepositorioJdbc implements DashboardVisaoGeralRepositorio {

    private final JdbcTemplate jdbc;

    DashboardVisaoGeralRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public VisaoGeralDashboard consultar(FiltroTemporalDashboard filtro) {
        AgregadoAtendimento atual = atendimentos(filtro.periodoAtual());
        AgregadoAtendimento anterior = atendimentos(List.of(filtro.periodoAnterior()));
        long atendimentosAcumulados = contarAte("atendimento", "iniciado_em", filtro);

        AgregadoAvaliacao avaliacaoAtual = avaliacoes(filtro.periodoAtual());
        AgregadoAvaliacao avaliacaoAnterior = avaliacoes(List.of(filtro.periodoAnterior()));

        AgregadoVendas vendasAtual = vendas(filtro.periodoAtual(), filtro.periodoDeOriginacao());
        AgregadoVendas vendasAnterior =
                vendas(List.of(filtro.periodoAnterior()), filtro.periodoDeOriginacao());
        long vendasAcumuladas = vendasAte(filtro);

        long leadsAtuais = contarLeadsDaConversao(filtro, true);
        long leadsAnteriores = contarLeadsDaConversao(filtro, false);
        BigDecimal taxaAtual = percentual(vendasAtual.total(), leadsAtuais);
        BigDecimal taxaAnterior = percentual(vendasAnterior.total(), leadsAnteriores);

        return new VisaoGeralDashboard(
                new VisaoGeralDashboard.Periodo(filtro.ano(), filtro.meses()),
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
                        ranking(filtro), vendasAtual.semResponsavel()));
    }

    private AgregadoAtendimento atendimentos(List<Intervalo> periodos) {
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

    private AgregadoAvaliacao avaliacoes(List<Intervalo> periodos) {
        FiltroSql filtro = periodos("criado_em", periodos);
        return jdbc.queryForObject(
                "SELECT count(*) AS quantidade, avg(nota) AS media FROM avaliacao WHERE "
                        + filtro.clausula(),
                (linha, indice) -> new AgregadoAvaliacao(
                        linha.getLong("quantidade"), linha.getBigDecimal("media")),
                filtro.parametros().toArray());
    }

    private AgregadoVendas vendas(List<Intervalo> periodos, Intervalo origem) {
        FiltroSql eventos = periodos("e.criado_em", periodos);
        FiltroSql originacao = origem == null ? FiltroSql.vazio() : intervalo("l.criado_em", origem);
        List<Object> parametros = new ArrayList<>(eventos.parametros());
        parametros.addAll(originacao.parametros());
        String porOrigem = originacao.vazia() ? "" : " AND " + originacao.clausula();
        return jdbc.queryForObject(
                """
                WITH vendas AS (
                    SELECT DISTINCT ON (e.lead_id)
                           e.lead_id,
                           NULLIF(e.dados ->> 'responsavel_id', '')::uuid AS responsavel_id
                      FROM evento_timeline e
                      JOIN lead l ON l.id = e.lead_id
                     WHERE e.tipo = 'ETAPA_ALTERADA'
                       AND e.dados ->> 'resultado_novo' = 'GANHO'
                       AND %s%s
                     ORDER BY e.lead_id, e.criado_em, e.id
                )
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE responsavel_id IS NULL) AS sem_responsavel
                  FROM vendas
                """.formatted(eventos.clausula(), porOrigem),
                (linha, indice) ->
                        new AgregadoVendas(linha.getLong("total"), linha.getLong("sem_responsavel")),
                parametros.toArray());
    }

    private long vendasAte(FiltroTemporalDashboard filtro) {
        FiltroSql origem = filtro.periodoDeOriginacao() == null
                ? FiltroSql.vazio()
                : intervalo("l.criado_em", filtro.periodoDeOriginacao());
        List<Object> parametros = new ArrayList<>();
        parametros.add(Timestamp.from(filtro.fimDoPeriodoAtual()));
        parametros.addAll(origem.parametros());
        String porOrigem = origem.vazia() ? "" : " AND " + origem.clausula();
        Long total = jdbc.queryForObject(
                """
                SELECT count(DISTINCT e.lead_id)
                  FROM evento_timeline e
                  JOIN lead l ON l.id = e.lead_id
                 WHERE e.tipo = 'ETAPA_ALTERADA'
                   AND e.dados ->> 'resultado_novo' = 'GANHO'
                   AND e.criado_em < ?%s
                """.formatted(porOrigem),
                Long.class,
                parametros.toArray());
        return total == null ? 0 : total;
    }

    private long contarLeadsDaConversao(FiltroTemporalDashboard filtro, boolean atual) {
        if (filtro.periodoDeOriginacao() != null) {
            return contarLeads(List.of(filtro.periodoDeOriginacao()));
        }
        return contarLeads(atual ? filtro.periodoAtual() : List.of(filtro.periodoAnterior()));
    }

    private long contarLeads(List<Intervalo> periodos) {
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

    private List<VisaoGeralDashboard.AtendenteNoRanking> ranking(
            FiltroTemporalDashboard filtro) {
        FiltroSql eventos = periodos("e.criado_em", filtro.periodoAtual());
        FiltroSql origem = filtro.periodoDeOriginacao() == null
                ? FiltroSql.vazio()
                : intervalo("l.criado_em", filtro.periodoDeOriginacao());
        List<Object> parametros = new ArrayList<>(eventos.parametros());
        parametros.addAll(origem.parametros());
        String porOrigem = origem.vazia() ? "" : " AND " + origem.clausula();
        return jdbc.query(
                """
                WITH vendas AS (
                    SELECT DISTINCT ON (e.lead_id)
                           e.lead_id,
                           NULLIF(e.dados ->> 'responsavel_id', '')::uuid AS responsavel_id
                      FROM evento_timeline e
                      JOIN lead l ON l.id = e.lead_id
                     WHERE e.tipo = 'ETAPA_ALTERADA'
                       AND e.dados ->> 'resultado_novo' = 'GANHO'
                       AND %s%s
                     ORDER BY e.lead_id, e.criado_em, e.id
                )
                SELECT u.id, u.nome, count(*) AS vendas
                  FROM vendas v
                  JOIN usuario u ON u.id = v.responsavel_id
                 GROUP BY u.id, u.nome
                 ORDER BY vendas DESC, u.nome
                """.formatted(eventos.clausula(), porOrigem),
                (linha, indice) -> new VisaoGeralDashboard.AtendenteNoRanking(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getLong("vendas")),
                parametros.toArray());
    }

    private long contarAte(String tabela, String coluna, FiltroTemporalDashboard filtro) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE " + coluna + " < ?",
                Long.class,
                Timestamp.from(filtro.fimDoPeriodoAtual()));
        return total == null ? 0 : total;
    }

    private static FiltroSql periodos(String coluna, List<Intervalo> periodos) {
        List<String> partes = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        for (Intervalo periodo : periodos) {
            partes.add("(" + coluna + " >= ? AND " + coluna + " < ?)");
            parametros.add(Timestamp.from(periodo.inicioInclusivo()));
            parametros.add(Timestamp.from(periodo.fimExclusivo()));
        }
        return new FiltroSql("(" + String.join(" OR ", partes) + ")", parametros);
    }

    private static FiltroSql intervalo(String coluna, Intervalo intervalo) {
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

    private record AgregadoVendas(long total, long semResponsavel) {}

    private record EtapaBruta(UUID id, String nome, int ordem, String corVisual, long quantidade) {}
}
