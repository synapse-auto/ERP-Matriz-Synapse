package com.synapse.crm.relatorios.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class FiltroTemporalDashboardTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Test
    void deDias_hoje_comparaComODiaAnteriorDaMesmaDuracao() {
        LocalDate hoje = LocalDate.of(2026, 8, 28);
        FiltroTemporalDashboard filtro = FiltroTemporalDashboard.deDias(hoje, hoje, null, null, FUSO);

        assertThat(filtro.recorteInicio()).isEqualTo(hoje);
        assertThat(filtro.recorteFim()).isEqualTo(hoje);
        assertThat(filtro.meses()).containsExactly(8);
        assertThat(ChronoUnit.HOURS.between(
                        filtro.periodoAtual().getFirst().inicioInclusivo(),
                        filtro.periodoAtual().getFirst().fimExclusivo()))
                .isEqualTo(24);
        assertThat(filtro.periodoAnterior().fimExclusivo())
                .isEqualTo(filtro.periodoAtual().getFirst().inicioInclusivo());
        assertThat(ChronoUnit.HOURS.between(
                        filtro.periodoAnterior().inicioInclusivo(), filtro.periodoAnterior().fimExclusivo()))
                .isEqualTo(24);
    }

    @Test
    void deDias_seteDias_comparaComAJanelaImediatamenteAnterior() {
        LocalDate fim = LocalDate.of(2026, 8, 28);
        LocalDate inicio = fim.minusDays(6);
        FiltroTemporalDashboard filtro = FiltroTemporalDashboard.deDias(inicio, fim, null, null, FUSO);

        assertThat(ChronoUnit.DAYS.between(
                        filtro.periodoAtual().getFirst().inicioInclusivo(),
                        filtro.periodoAtual().getFirst().fimExclusivo()))
                .isEqualTo(7);
        assertThat(ChronoUnit.DAYS.between(
                        filtro.periodoAnterior().inicioInclusivo(), filtro.periodoAnterior().fimExclusivo()))
                .isEqualTo(7);
        assertThat(filtro.periodoAnterior().fimExclusivo())
                .isEqualTo(filtro.periodoAtual().getFirst().inicioInclusivo());
    }

    @Test
    void deEntrada_soInicio_reprova() {
        assertThatThrownBy(() -> FiltroTemporalDashboard.deEntrada(
                        2026,
                        null,
                        LocalDate.of(2026, 8, 28),
                        null,
                        null,
                        null,
                        FUSO))
                .isInstanceOf(FiltroDashboardInvalidoException.class);
    }

    @Test
    void deEntrada_semAnoNemRecorte_reprova() {
        assertThatThrownBy(() -> FiltroTemporalDashboard.deEntrada(null, null, null, null, null, null, FUSO))
                .isInstanceOf(FiltroDashboardInvalidoException.class);
    }
}
