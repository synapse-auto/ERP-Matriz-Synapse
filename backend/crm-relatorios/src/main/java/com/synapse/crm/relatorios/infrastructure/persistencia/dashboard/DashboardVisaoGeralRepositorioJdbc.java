package com.synapse.crm.relatorios.infrastructure.persistencia.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
import com.synapse.crm.sharedkernel.avaliacao.EscalaDeAvaliacao;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

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
        ResultadoMensal<AgregadoAtendimento> atendimentosMensais = atendimentosMensais(filtro);
        AgregadoAtendimento atual = atendimentosMensais.total();
        AgregadoAtendimento anterior = atendimentos(List.of(filtro.periodoAnterior()));
        long atendimentosAcumulados = contarAte("atendimento", "iniciado_em", filtro);

        ResultadoMensal<AgregadoAvaliacao> avaliacoesMensais = avaliacoesMensais(filtro);
        AgregadoAvaliacao avaliacaoAtual = avaliacoesMensais.total();
        AgregadoAvaliacao avaliacaoAnterior = avaliacoes(List.of(filtro.periodoAnterior()));

        ResultadoMensal<AgregadoResolucaoIa> resolucoesMensais = resolucoesMensais(filtro);
        AgregadoResolucaoIa resolucaoIaAtual = resolucoesMensais.total();
        AgregadoResolucaoIa resolucaoIaAnterior =
                resolucaoPorIa(List.of(filtro.periodoAnterior()));
        BigDecimal taxaResolucaoIaAtual = percentual(
                resolucaoIaAtual.semTransferencia(), resolucaoIaAtual.finalizados());
        BigDecimal taxaResolucaoIaAnterior = percentual(
                resolucaoIaAnterior.semTransferencia(), resolucaoIaAnterior.finalizados());

        AgregacaoDeVendasRepositorio.VendasComSerie vendasComSerie = vendas.agregarComSerie(
                filtro.periodoAtual(), filtro.periodoDeOriginacao(), filtro.fusoHorario());
        AgregacaoDeVendas vendasAtual = vendasComSerie.agregado();
        long vendasAnteriores =
                vendas.totalDeVendas(List.of(filtro.periodoAnterior()), filtro.periodoDeOriginacao());
        long vendasAcumuladas =
                vendas.contarAte(filtro.fimDoPeriodoAtual(), filtro.periodoDeOriginacao());

        ResultadoMensal<Long> leadsMensais = leadsMensais(filtro);
        long novosLeadsAtual = leadsMensais.total();
        long novosLeadsAnterior = contarLeads(List.of(filtro.periodoAnterior()));
        long leadsDaOriginacao = filtro.periodoDeOriginacao() == null
                ? 0
                : contarLeads(List.of(filtro.periodoDeOriginacao()));
        long leadsAtuais = filtro.periodoDeOriginacao() == null
                ? novosLeadsAtual
                : leadsDaOriginacao;
        long leadsAnteriores = filtro.periodoDeOriginacao() == null
                ? novosLeadsAnterior
                : leadsDaOriginacao;
        BigDecimal taxaAtual = percentual(vendasAtual.total(), leadsAtuais);
        BigDecimal taxaAnterior = percentual(vendasAnteriores, leadsAnteriores);

        List<VisaoGeralDashboard.AtendenteNaAvaliacao> avaliacoesDoPeriodo =
                rankingAvaliacoes(filtro.periodoAtual());
        FunilResultado funil = funil(filtro);

        return new VisaoGeralDashboard(
                new VisaoGeralDashboard.Periodo(
                        filtro.ano(), filtro.meses(), filtro.recorteInicio(), filtro.recorteFim()),
                new VisaoGeralDashboard.Atendimentos(
                        atual.quantidade(),
                        atendimentosAcumulados,
                        Comparativo.percentual(decimal(atual.quantidade()), decimal(anterior.quantidade()))),
                new VisaoGeralDashboard.NovosLeads(
                        novosLeadsAtual,
                        Comparativo.percentual(decimal(novosLeadsAtual), decimal(novosLeadsAnterior))),
                new VisaoGeralDashboard.TempoMedioAtendimento(
                        segundos(atual.mediaSegundos()),
                        Comparativo.percentual(atual.mediaSegundos(), anterior.mediaSegundos())),
                new VisaoGeralDashboard.AvaliacaoMedia(
                        escala(avaliacaoAtual.media()),
                        EscalaDeAvaliacao.NOTA_MAXIMA,
                        avaliacaoAtual.quantidade(),
                        new VisaoGeralDashboard.DistribuicaoDeAvaliacoes(
                                avaliacaoAtual.otimo(), avaliacaoAtual.bom(), avaliacaoAtual.ruim()),
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
                                decimal(vendasAtual.total()), decimal(vendasAnteriores))),
                new VisaoGeralDashboard.TaxaConversao(
                        taxaAtual,
                        vendasAtual.total(),
                        leadsAtuais,
                        Comparativo.pontosPercentuais(taxaAtual, taxaAnterior)),
                statusAoVivo(filtro),
                funil.etapas(),
                funil.perdidos(),
                mensagensPorHora(filtro),
                new VisaoGeralDashboard.RankingDeVendas(
                        vendasAtual.porAtendente().stream()
                                .map(item -> new VisaoGeralDashboard.AtendenteNoRanking(
                                        item.atendenteId(), item.atendenteNome(), item.vendas()))
                                .toList(),
                        vendasAtual.semResponsavel()),
                new VisaoGeralDashboard.RankingDeAvaliacoes(avaliacoesDoPeriodo),
                equipeDesempenho(filtro, vendasAtual, avaliacoesDoPeriodo),
                seriesMensais(
                        filtro,
                        leadsDaOriginacao,
                        atendimentosMensais.porMes(),
                        leadsMensais.porMes(),
                        avaliacoesMensais.porMes(),
                        resolucoesMensais.porMes(),
                        vendasComSerie.porMes()));
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
        FiltroSql filtro = semAdministradores(periodos("a.criado_em", periodos), "u");
        return jdbc.queryForObject(
                """
                SELECT count(*) AS quantidade,
                       avg(a.nota) AS media,
                       count(*) FILTER (WHERE a.nota BETWEEN 9 AND 10) AS otimo,
                       count(*) FILTER (WHERE a.nota BETWEEN 7 AND 8) AS bom,
                       count(*) FILTER (WHERE a.nota BETWEEN 0 AND 6) AS ruim
                  FROM avaliacao a
                  JOIN usuario u ON u.id = a.atendente_id
                 WHERE %s
                """.formatted(filtro.clausula()),
                (linha, indice) -> new AgregadoAvaliacao(
                        linha.getLong("quantidade"),
                        linha.getBigDecimal("media"),
                        linha.getLong("otimo"),
                        linha.getLong("bom"),
                        linha.getLong("ruim")),
                filtro.parametros().toArray());
    }

    private List<VisaoGeralDashboard.AtendenteNaAvaliacao> rankingAvaliacoes(
            List<IntervaloTemporal> periodos) {
        FiltroSql filtro = semAdministradores(periodos("a.criado_em", periodos), "u");
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
     * A origem do papel e o enum do shared-kernel; o cast preserva o tipo do enum PostgreSQL sem
     * reproduzir a regra de identificacao de administrador em cada consulta.
     */
    private static FiltroSql semAdministradores(FiltroSql filtro, String aliasUsuario) {
        List<Object> parametros = new ArrayList<>(filtro.parametros());
        parametros.add(PapelUsuario.ADMINISTRADOR.name());
        return new FiltroSql(
                filtro.clausula()
                        + " AND " + aliasUsuario + ".papel <> CAST(? AS papel_usuario)",
                parametros);
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

    private long contarLeads(List<IntervaloTemporal> periodos) {
        FiltroSql temporal = periodos("criado_em", periodos);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM lead WHERE " + temporal.clausula(),
                Long.class,
                temporal.parametros().toArray());
        return total == null ? 0 : total;
    }

    /**
     * "Perdido" fica fora da sequência ordenada do funil (etapas com {@code resultado = PERDIDO})
     * e vira um agregado à parte: uma perda pode vir de qualquer etapa em andamento, então misturá-
     * la na ordem por {@code ordem} distorceria o percentual de passagem da etapa seguinte (bug
     * latente do comportamento anterior — ver relatório da E197).
     */
    private FunilResultado funil(FiltroTemporalDashboard filtro) {
        FiltroSql coorte = filtro.periodoDeOriginacao() == null
                ? periodos("l.criado_em", filtro.periodoAtual())
                : intervalo("l.criado_em", filtro.periodoDeOriginacao());
        List<EtapaBruta> etapas = jdbc.query(
                """
                SELECT e.id, e.nome, e.ordem, e.cor_visual, e.resultado::text AS resultado,
                       count(l.id) AS quantidade
                  FROM etapa_atendimento e
                  LEFT JOIN lead l ON l.etapa_atendimento_id = e.id AND %s
                 GROUP BY e.id, e.nome, e.ordem, e.cor_visual, e.resultado
                 ORDER BY e.ordem, e.nome
                """.formatted(coorte.clausula()),
                (linha, indice) -> new EtapaBruta(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getInt("ordem"),
                        linha.getString("cor_visual"),
                        linha.getString("resultado"),
                        linha.getLong("quantidade")),
                coorte.parametros().toArray());
        List<VisaoGeralDashboard.EtapaDoFunil> resposta = new ArrayList<>();
        long perdidos = 0;
        long anterior = 0;
        int posicao = 0;
        for (EtapaBruta etapa : etapas) {
            if ("PERDIDO".equals(etapa.resultado())) {
                perdidos += etapa.quantidade();
                continue;
            }
            BigDecimal passagem = posicao == 0 || anterior == 0
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
            posicao++;
        }
        return new FunilResultado(resposta, perdidos);
    }

    @Override
    public VisaoGeralDashboard.StatusAoVivo consultarStatusAoVivo(FiltroTemporalDashboard filtro) {
        return statusAoVivo(filtro);
    }

    private VisaoGeralDashboard.StatusAoVivo statusAoVivo(FiltroTemporalDashboard filtro) {
        long[] atendimentosPorStatus = jdbc.queryForObject(
                """
                SELECT count(*) FILTER (WHERE status = 'EM_IA') AS em_ia,
                       count(*) FILTER (WHERE status = 'EM_ATENDIMENTO') AS em_atendimento
                  FROM atendimento
                 WHERE status IN ('EM_IA', 'EM_ATENDIMENTO')
                """,
                (linha, indice) -> new long[] {
                    linha.getLong("em_ia"), linha.getLong("em_atendimento")
                });
        ZoneId fuso = filtro.fusoHorario();
        LocalDate hoje = LocalDate.now(fuso);
        IntervaloTemporal intervaloHoje = new IntervaloTemporal(
                hoje.atStartOfDay(fuso).toInstant(), hoje.plusDays(1).atStartOfDay(fuso).toInstant());
        long leadsNovosHoje = contarLeads(List.of(intervaloHoje));
        long vendasHoje = vendas.totalDeVendas(List.of(intervaloHoje), null);
        VisaoGeralDashboard.AtendentesOnline atendentesOnline = jdbc.queryForObject(
                """
                SELECT count(*) FILTER (WHERE status_presenca = 'ONLINE') AS online,
                       count(*) AS total
                  FROM usuario
                 WHERE ativo = TRUE AND papel IN ('ATENDENTE', 'SUBGESTOR')
                """,
                (linha, indice) -> new VisaoGeralDashboard.AtendentesOnline(
                        linha.getLong("online"), linha.getLong("total")));
        return new VisaoGeralDashboard.StatusAoVivo(
                atendimentosPorStatus[0],
                atendimentosPorStatus[1],
                leadsNovosHoje,
                vendasHoje,
                atendentesOnline);
    }

    /**
     * Junta os três read models de desempenho por atendente já existentes (atendimentos, vendas,
     * avaliações) num único conjunto ordenado por vendas — a tabela "Equipe · desempenho" do
     * mockup, exceto as colunas de conversão e 1ª resposta (ver Javadoc de AtendenteDesempenho).
     */
    private List<VisaoGeralDashboard.AtendenteDesempenho> equipeDesempenho(
            FiltroTemporalDashboard filtro,
            AgregacaoDeVendas vendasAtual,
            List<VisaoGeralDashboard.AtendenteNaAvaliacao> avaliacoesDoPeriodo) {
        FiltroSql filtroAtendimentos = semAdministradores(periodos("a.iniciado_em", filtro.periodoAtual()), "u");
        List<LinhaDeAtendimentoPorAtendente> atendimentosPorAtendente = jdbc.query(
                """
                SELECT a.atendente_id AS id, u.nome, count(*) AS quantidade
                  FROM atendimento a
                  JOIN usuario u ON u.id = a.atendente_id
                 WHERE a.atendente_id IS NOT NULL AND %s
                 GROUP BY a.atendente_id, u.nome
                """.formatted(filtroAtendimentos.clausula()),
                (linha, indice) -> new LinhaDeAtendimentoPorAtendente(
                        linha.getObject("id", UUID.class), linha.getString("nome"), linha.getLong("quantidade")),
                filtroAtendimentos.parametros().toArray());

        Map<UUID, String> nomes = new LinkedHashMap<>();
        Map<UUID, Long> atendimentosPorId = new LinkedHashMap<>();
        for (LinhaDeAtendimentoPorAtendente linha : atendimentosPorAtendente) {
            nomes.put(linha.id(), linha.nome());
            atendimentosPorId.put(linha.id(), linha.quantidade());
        }
        Map<UUID, Long> vendasPorId = new LinkedHashMap<>();
        for (var item : vendasAtual.porAtendente()) {
            nomes.put(item.atendenteId(), item.atendenteNome());
            vendasPorId.put(item.atendenteId(), item.vendas());
        }
        Map<UUID, BigDecimal> notaPorId = new LinkedHashMap<>();
        Map<UUID, Long> avaliacoesPorId = new LinkedHashMap<>();
        for (VisaoGeralDashboard.AtendenteNaAvaliacao item : avaliacoesDoPeriodo) {
            nomes.put(item.id(), item.nome());
            notaPorId.put(item.id(), item.media());
            avaliacoesPorId.put(item.id(), item.quantidade());
        }

        return nomes.keySet().stream()
                .map(id -> new VisaoGeralDashboard.AtendenteDesempenho(
                        id,
                        nomes.get(id),
                        atendimentosPorId.getOrDefault(id, 0L),
                        vendasPorId.getOrDefault(id, 0L),
                        notaPorId.get(id),
                        avaliacoesPorId.getOrDefault(id, 0L)))
                .sorted(Comparator
                        .comparingLong(VisaoGeralDashboard.AtendenteDesempenho::vendas)
                        .reversed()
                        .thenComparing(VisaoGeralDashboard.AtendenteDesempenho::nome))
                .toList();
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

    private List<VisaoGeralDashboard.PontoMensal> seriesMensais(
            FiltroTemporalDashboard filtro,
            long leadsDaOriginacao,
            Map<YearMonth, AgregadoAtendimento> atendimentos,
            Map<YearMonth, Long> leads,
            Map<YearMonth, AgregadoAvaliacao> avaliacoes,
            Map<YearMonth, AgregadoResolucaoIa> resolucoes,
            Map<YearMonth, Long> vendasMensais) {
        YearMonth mesAtual = YearMonth.now(filtro.fusoHorario());
        TreeSet<YearMonth> meses = new TreeSet<>();
        for (IntervaloTemporal intervalo : filtro.periodoAtual()) {
            YearMonth mes = YearMonth.from(intervalo.inicioInclusivo().atZone(filtro.fusoHorario()));
            YearMonth ultimo = YearMonth.from(
                    intervalo.fimExclusivo().minusNanos(1).atZone(filtro.fusoHorario()));
            while (!mes.isAfter(ultimo)) {
                meses.add(mes);
                mes = mes.plusMonths(1);
            }
        }
        return meses.stream()
                .map(mes -> {
                    if (mes.isAfter(mesAtual)) {
                        return new VisaoGeralDashboard.PontoMensal(
                                mes, false, false, null, null, null, null, null, null, null);
                    }
                    AgregadoAtendimento atendimento =
                            atendimentos.getOrDefault(mes, new AgregadoAtendimento(0, null));
                    AgregadoResolucaoIa resolucao =
                            resolucoes.getOrDefault(mes, new AgregadoResolucaoIa(0, 0));
                    long novos = leads.getOrDefault(mes, 0L);
                    long fechadas = vendasMensais.getOrDefault(mes, 0L);
                    long denominador = filtro.periodoDeOriginacao() == null
                            ? novos
                            : leadsDaOriginacao;
                    boolean mesCompleto = filtro.periodoAtual().stream().anyMatch(intervalo ->
                            !intervalo.inicioInclusivo().isAfter(mes.atDay(1).atStartOfDay(filtro.fusoHorario()).toInstant())
                                    && !intervalo.fimExclusivo().isBefore(mes.plusMonths(1).atDay(1).atStartOfDay(filtro.fusoHorario()).toInstant()));
                    return new VisaoGeralDashboard.PontoMensal(
                            mes,
                            mes.equals(mesAtual) || !mesCompleto,
                            true,
                            atendimento.quantidade(),
                            novos,
                            segundos(atendimento.mediaSegundos()),
                            fechadas,
                            percentual(fechadas, denominador),
                            escala(avaliacoes.containsKey(mes) ? avaliacoes.get(mes).media() : null),
                            percentual(resolucao.semTransferencia(), resolucao.finalizados()));
                })
                .toList();
    }

    private ResultadoMensal<AgregadoAtendimento> atendimentosMensais(FiltroTemporalDashboard filtro) {
        FiltroSql temporal = periodos("iniciado_em", filtro.periodoAtual());
        return jdbc.query(
                """
                WITH recorte AS (
                    SELECT date_trunc('month', iniciado_em AT TIME ZONE ?)::date AS mes,
                           iniciado_em, finalizado_em
                      FROM atendimento WHERE %s
                )
                SELECT mes,
                       count(*) AS quantidade,
                       avg(extract(epoch FROM (finalizado_em - iniciado_em)))
                           FILTER (WHERE finalizado_em IS NOT NULL) AS media_segundos
                  FROM recorte
                 GROUP BY ROLLUP(mes)
                """.formatted(temporal.clausula()),
                linha -> {
                    Map<YearMonth, AgregadoAtendimento> porMes = new LinkedHashMap<>();
                    AgregadoAtendimento total = new AgregadoAtendimento(0, null);
                    while (linha.next()) {
                        AgregadoAtendimento agregado = new AgregadoAtendimento(
                                linha.getLong("quantidade"), linha.getBigDecimal("media_segundos"));
                        if (linha.getDate("mes") == null) {
                            total = agregado;
                        } else {
                            porMes.put(YearMonth.from(linha.getDate("mes").toLocalDate()), agregado);
                        }
                    }
                    return new ResultadoMensal<>(total, porMes);
                },
                comFusoAntes(filtro, temporal));
    }

    private ResultadoMensal<Long> leadsMensais(FiltroTemporalDashboard filtro) {
        FiltroSql temporal = periodos("criado_em", filtro.periodoAtual());
        return jdbc.query(
                """
                WITH recorte AS (
                    SELECT date_trunc('month', criado_em AT TIME ZONE ?)::date AS mes
                      FROM lead WHERE %s
                )
                SELECT mes,
                       count(*) AS quantidade
                  FROM recorte
                 GROUP BY ROLLUP(mes)
                """.formatted(temporal.clausula()),
                linha -> {
                    Map<YearMonth, Long> porMes = new LinkedHashMap<>();
                    long total = 0;
                    while (linha.next()) {
                        if (linha.getDate("mes") == null) {
                            total = linha.getLong("quantidade");
                        } else {
                            porMes.put(
                                    YearMonth.from(linha.getDate("mes").toLocalDate()),
                                    linha.getLong("quantidade"));
                        }
                    }
                    return new ResultadoMensal<>(total, porMes);
                },
                comFusoAntes(filtro, temporal));
    }

    private ResultadoMensal<AgregadoAvaliacao> avaliacoesMensais(FiltroTemporalDashboard filtro) {
        FiltroSql temporal = semAdministradores(periodos("a.criado_em", filtro.periodoAtual()), "u");
        return jdbc.query(
                """
                WITH recorte AS (
                    SELECT date_trunc('month', a.criado_em AT TIME ZONE ?)::date AS mes,
                           a.nota
                      FROM avaliacao a
                      JOIN usuario u ON u.id = a.atendente_id
                     WHERE %s
                )
                SELECT mes, count(*) AS quantidade, avg(nota) AS media,
                       count(*) FILTER (WHERE nota BETWEEN 9 AND 10) AS otimo,
                       count(*) FILTER (WHERE nota BETWEEN 7 AND 8) AS bom,
                       count(*) FILTER (WHERE nota BETWEEN 0 AND 6) AS ruim
                  FROM recorte GROUP BY ROLLUP(mes)
                """.formatted(temporal.clausula()),
                linha -> {
                    Map<YearMonth, AgregadoAvaliacao> porMes = new LinkedHashMap<>();
                    AgregadoAvaliacao total = new AgregadoAvaliacao(0, null, 0, 0, 0);
                    while (linha.next()) {
                        AgregadoAvaliacao agregado = new AgregadoAvaliacao(
                                linha.getLong("quantidade"),
                                linha.getBigDecimal("media"),
                                linha.getLong("otimo"),
                                linha.getLong("bom"),
                                linha.getLong("ruim"));
                        if (linha.getDate("mes") == null) {
                            total = agregado;
                        } else {
                            porMes.put(YearMonth.from(linha.getDate("mes").toLocalDate()), agregado);
                        }
                    }
                    return new ResultadoMensal<>(total, porMes);
                },
                comFusoAntes(filtro, temporal));
    }

    private ResultadoMensal<AgregadoResolucaoIa> resolucoesMensais(FiltroTemporalDashboard filtro) {
        FiltroSql temporal = periodos("a.finalizado_em", filtro.periodoAtual());
        return jdbc.query(
                """
                WITH recorte AS (
                    SELECT date_trunc('month', a.finalizado_em AT TIME ZONE ?)::date AS mes,
                           NOT EXISTS (
                               SELECT 1 FROM evento_timeline e
                                WHERE e.atendimento_id = a.id
                                  AND e.tipo IN ('LEAD_TRANSFERIDO_POR_ENVIO', 'ATENDIMENTO_TRANSFERIDO')
                           ) AS sem_transferencia
                      FROM atendimento a WHERE %s
                )
                SELECT mes, count(*) AS finalizados,
                       count(*) FILTER (WHERE sem_transferencia) AS sem_transferencia
                  FROM recorte GROUP BY ROLLUP(mes)
                """.formatted(temporal.clausula()),
                linha -> {
                    Map<YearMonth, AgregadoResolucaoIa> porMes = new LinkedHashMap<>();
                    AgregadoResolucaoIa total = new AgregadoResolucaoIa(0, 0);
                    while (linha.next()) {
                        AgregadoResolucaoIa agregado = new AgregadoResolucaoIa(
                                linha.getLong("finalizados"), linha.getLong("sem_transferencia"));
                        if (linha.getDate("mes") == null) {
                            total = agregado;
                        } else {
                            porMes.put(YearMonth.from(linha.getDate("mes").toLocalDate()), agregado);
                        }
                    }
                    return new ResultadoMensal<>(total, porMes);
                },
                comFusoAntes(filtro, temporal));
    }

    private static Object[] comFusoAntes(FiltroTemporalDashboard filtro, FiltroSql temporal) {
        List<Object> parametros = new ArrayList<>();
        parametros.add(filtro.fusoHorario().getId());
        parametros.addAll(temporal.parametros());
        return parametros.toArray();
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

    private record AgregadoAvaliacao(long quantidade, BigDecimal media, long otimo, long bom, long ruim) {}

    private record AgregadoResolucaoIa(long finalizados, long semTransferencia) {}

    private record EtapaBruta(
            UUID id, String nome, int ordem, String corVisual, String resultado, long quantidade) {}

    private record FunilResultado(List<VisaoGeralDashboard.EtapaDoFunil> etapas, long perdidos) {}

    private record LinhaDeAtendimentoPorAtendente(UUID id, String nome, long quantidade) {}

    private record ResultadoMensal<T>(T total, Map<YearMonth, T> porMes) {}
}
