package com.synapse.crm.relatorios.infrastructure.persistencia.vendas;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.relatorios.application.vendas.AgregacaoDeVendasRepositorio;
import com.synapse.crm.relatorios.domain.IntervaloTemporal;
import com.synapse.crm.relatorios.domain.vendas.AgregacaoDeVendas;
import com.synapse.crm.relatorios.domain.vendas.AgregacaoDeVendas.VendasPorAtendente;

/** Consulta canonica de vendas: primeira transicao de cada lead para GANHO dentro do periodo. */
@Repository
class AgregacaoDeVendasRepositorioJdbc implements AgregacaoDeVendasRepositorio {

    private final JdbcTemplate jdbc;

    AgregacaoDeVendasRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AgregacaoDeVendas agregar(
            List<IntervaloTemporal> periodos, IntervaloTemporal periodoDeOriginacao) {
        FiltroSql eventos = periodos.isEmpty()
                ? FiltroSql.semRestricao()
                : periodos("e.criado_em", periodos);
        FiltroSql origem = periodoDeOriginacao == null
                ? FiltroSql.semRestricao()
                : intervalo("l.criado_em", periodoDeOriginacao);
        List<Object> parametros = new ArrayList<>(eventos.parametros());
        parametros.addAll(origem.parametros());

        List<LinhaDeVendas> linhas = jdbc.query(
                """
                WITH vendas AS (
                    SELECT DISTINCT ON (e.lead_id)
                           e.lead_id,
                           NULLIF(e.dados ->> 'responsavel_id', '')::uuid AS responsavel_id
                      FROM evento_timeline e
                      JOIN lead l ON l.id = e.lead_id
                     WHERE e.tipo = 'ETAPA_ALTERADA'
                       AND e.dados ->> 'resultado_novo' = 'GANHO'
                       AND %s
                       AND %s
                     ORDER BY e.lead_id, e.criado_em, e.id
                )
                SELECT v.responsavel_id, u.nome, count(*) AS vendas
                  FROM vendas v
                  LEFT JOIN usuario u ON u.id = v.responsavel_id
                 GROUP BY v.responsavel_id, u.nome
                 ORDER BY vendas DESC, u.nome NULLS LAST
                """.formatted(eventos.clausula(), origem.clausula()),
                (linha, indice) -> new LinhaDeVendas(
                        linha.getObject("responsavel_id", java.util.UUID.class),
                        linha.getString("nome"),
                        linha.getLong("vendas")),
                parametros.toArray());

        long semResponsavel = linhas.stream()
                .filter(linha -> linha.atendenteId() == null)
                .mapToLong(LinhaDeVendas::vendas)
                .sum();
        List<VendasPorAtendente> porAtendente = linhas.stream()
                .filter(linha -> linha.atendenteId() != null)
                .map(linha -> new VendasPorAtendente(
                        linha.atendenteId(), linha.atendenteNome(), linha.vendas()))
                .toList();
        long total = linhas.stream().mapToLong(LinhaDeVendas::vendas).sum();
        return new AgregacaoDeVendas(total, semResponsavel, porAtendente);
    }

    @Override
    public long contarAte(Instant fimExclusivo, IntervaloTemporal periodoDeOriginacao) {
        FiltroSql origem = periodoDeOriginacao == null
                ? FiltroSql.semRestricao()
                : intervalo("l.criado_em", periodoDeOriginacao);
        List<Object> parametros = new ArrayList<>();
        parametros.add(Timestamp.from(fimExclusivo));
        parametros.addAll(origem.parametros());
        Long total = jdbc.queryForObject(
                """
                SELECT count(DISTINCT e.lead_id)
                  FROM evento_timeline e
                  JOIN lead l ON l.id = e.lead_id
                 WHERE e.tipo = 'ETAPA_ALTERADA'
                   AND e.dados ->> 'resultado_novo' = 'GANHO'
                   AND e.criado_em < ?
                   AND %s
                """.formatted(origem.clausula()),
                Long.class,
                parametros.toArray());
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

    private record FiltroSql(String clausula, List<Object> parametros) {
        static FiltroSql semRestricao() {
            return new FiltroSql("TRUE", List.of());
        }
    }

    private record LinhaDeVendas(
            java.util.UUID atendenteId, String atendenteNome, long vendas) {}
}
