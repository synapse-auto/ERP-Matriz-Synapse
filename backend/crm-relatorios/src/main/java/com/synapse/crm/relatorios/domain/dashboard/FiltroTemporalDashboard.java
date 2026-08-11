package com.synapse.crm.relatorios.domain.dashboard;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Janelas temporais validadas do dashboard, sempre com fim exclusivo. */
public record FiltroTemporalDashboard(
        int ano,
        List<Integer> meses,
        List<Intervalo> periodoAtual,
        Intervalo periodoAnterior,
        Intervalo periodoDeOriginacao,
        ZoneId fusoHorario) {

    public FiltroTemporalDashboard {
        meses = List.copyOf(meses);
        periodoAtual = List.copyOf(periodoAtual);
        Objects.requireNonNull(periodoAnterior, "periodo anterior e obrigatorio");
        Objects.requireNonNull(fusoHorario, "fuso horario e obrigatorio");
        if (meses.isEmpty() || periodoAtual.isEmpty()) {
            throw new FiltroDashboardInvalidoException("ao menos um mes deve ser selecionado");
        }
    }

    public static FiltroTemporalDashboard de(
            int ano,
            List<Integer> mesesInformados,
            LocalDate origemInicio,
            LocalDate origemFim,
            ZoneId fusoHorario) {
        try {
            Year.of(ano);
        } catch (DateTimeException erro) {
            throw new FiltroDashboardInvalidoException("ano fora da faixa suportada");
        }
        List<Integer> meses = mesesInformados == null || mesesInformados.isEmpty()
                ? java.util.stream.IntStream.rangeClosed(1, 12).boxed().toList()
                : mesesInformados.stream().distinct().sorted().toList();
        if (meses.stream().anyMatch(mes -> mes < 1 || mes > 12)) {
            throw new FiltroDashboardInvalidoException("meses devem estar entre 1 e 12");
        }
        if ((origemInicio == null) != (origemFim == null)) {
            throw new FiltroDashboardInvalidoException(
                    "origemInicio e origemFim devem ser informados juntos");
        }
        if (origemInicio != null && origemInicio.isAfter(origemFim)) {
            throw new FiltroDashboardInvalidoException(
                    "origemInicio nao pode ser posterior a origemFim");
        }

        List<Intervalo> atuais = meses.stream()
                .map(mes -> intervaloDoMes(YearMonth.of(ano, mes), fusoHorario))
                .toList();
        YearMonth primeiroMes = YearMonth.of(ano, meses.getFirst());
        Instant fimAnterior = primeiroMes.atDay(1).atStartOfDay(fusoHorario).toInstant();
        Instant inicioAnterior = primeiroMes
                .minusMonths(meses.size())
                .atDay(1)
                .atStartOfDay(fusoHorario)
                .toInstant();
        Intervalo origem = origemInicio == null
                ? null
                : new Intervalo(
                        origemInicio.atStartOfDay(fusoHorario).toInstant(),
                        origemFim.plusDays(1).atStartOfDay(fusoHorario).toInstant());
        return new FiltroTemporalDashboard(
                ano,
                meses,
                atuais,
                new Intervalo(inicioAnterior, fimAnterior),
                origem,
                fusoHorario);
    }

    public Instant fimDoPeriodoAtual() {
        return periodoAtual.stream().map(Intervalo::fimExclusivo).max(Instant::compareTo).orElseThrow();
    }

    private static Intervalo intervaloDoMes(YearMonth mes, ZoneId fusoHorario) {
        return new Intervalo(
                mes.atDay(1).atStartOfDay(fusoHorario).toInstant(),
                mes.plusMonths(1).atDay(1).atStartOfDay(fusoHorario).toInstant());
    }

    public record Intervalo(Instant inicioInclusivo, Instant fimExclusivo) {
        public Intervalo {
            Objects.requireNonNull(inicioInclusivo, "inicio e obrigatorio");
            Objects.requireNonNull(fimExclusivo, "fim e obrigatorio");
            if (!inicioInclusivo.isBefore(fimExclusivo)) {
                throw new IllegalArgumentException("intervalo deve ter inicio anterior ao fim");
            }
        }
    }
}
