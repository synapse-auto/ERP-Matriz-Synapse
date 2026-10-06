package com.synapse.crm.atendimento.domain.finalizacaomassa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class PeriodoDeFinalizacaoTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate QUINTA = LocalDate.of(2026, 10, 1);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 3);

    private static PeriodoDeFinalizacao periodo(LocalDate de, LocalDate ate, LocalTime hi, LocalTime hf) {
        return PeriodoDeFinalizacao.de(de, ate, hi, hf, SAO_PAULO, 31);
    }

    @Test
    void semHorarioAsDuasDatasSaoInclusivasNoFusoDaInstancia() {
        var p = periodo(QUINTA, SABADO, null, null);

        // 00:00 de quinta em Sao Paulo (-03:00) = 03:00Z; o fim e 00:00 de domingo = 03:00Z de domingo.
        assertThat(p.inicio()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(p.fim()).isEqualTo(Instant.parse("2026-10-04T03:00:00Z"));
    }

    @Test
    void inicioEInclusivoEFimEExclusivo() {
        var p = periodo(QUINTA, SABADO, null, null);

        assertThat(p.contem(Instant.parse("2026-10-01T03:00:00Z"))).isTrue();
        assertThat(p.contem(Instant.parse("2026-10-01T02:59:59.999Z"))).isFalse();
        assertThat(p.contem(Instant.parse("2026-10-04T02:59:59.999Z"))).isTrue();
        assertThat(p.contem(Instant.parse("2026-10-04T03:00:00Z"))).isFalse();
    }

    @Test
    void horarioFinalEInclusivoNoMinuto() {
        var p = periodo(QUINTA, QUINTA, LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertThat(p.contem(Instant.parse("2026-10-01T21:00:59.999Z"))).isTrue(); // 18:00:59.999 -03
        assertThat(p.contem(Instant.parse("2026-10-01T21:01:00Z"))).isFalse(); // 18:01
        assertThat(p.contem(Instant.parse("2026-10-01T12:00:00Z"))).isTrue(); // 09:00 exato entra
        assertThat(p.contem(Instant.parse("2026-10-01T11:59:59Z"))).isFalse();
    }

    @Test
    void horariosValemSoNasPontasDoPeriodo() {
        var p = periodo(QUINTA, SABADO, LocalTime.of(14, 0), LocalTime.of(10, 0));

        // Quinta so a partir das 14:00; sabado so ate 10:00:59; sexta inteira dentro.
        assertThat(p.contem(Instant.parse("2026-10-01T16:59:59Z"))).isFalse(); // quinta 13:59
        assertThat(p.contem(Instant.parse("2026-10-01T17:00:00Z"))).isTrue(); // quinta 14:00
        assertThat(p.contem(Instant.parse("2026-10-02T05:00:00Z"))).isTrue(); // sexta 02:00
        assertThat(p.contem(Instant.parse("2026-10-03T13:00:59Z"))).isTrue(); // sabado 10:00:59 (minuto final)
        assertThat(p.contem(Instant.parse("2026-10-03T13:01:00Z"))).isFalse(); // sabado 10:01
    }

    @Test
    void umaDataSoCobreODiaTodo() {
        var p = periodo(QUINTA, QUINTA, null, null);

        assertThat(p.inicio()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(p.fim()).isEqualTo(Instant.parse("2026-10-02T03:00:00Z"));
    }

    @Test
    void periodoInvertidoEhRecusado() {
        assertThatThrownBy(() -> periodo(SABADO, QUINTA, null, null))
                .isInstanceOfSatisfying(FinalizacaoEmMassaException.class, e -> assertThat(e.codigo()).isEqualTo("PERIODO_INVERTIDO"));
    }

    @Test
    void horarioFinalAntesDoInicialNoMesmoDiaEhRecusado() {
        assertThatThrownBy(() -> periodo(QUINTA, QUINTA, LocalTime.of(18, 0), LocalTime.of(9, 0)))
                .isInstanceOfSatisfying(FinalizacaoEmMassaException.class, e -> assertThat(e.codigo()).isEqualTo("PERIODO_INVERTIDO"));
    }

    @Test
    void mesmoHorarioInicialEFinalCobreUmMinuto() {
        var p = periodo(QUINTA, QUINTA, LocalTime.of(9, 30), LocalTime.of(9, 30));

        assertThat(p.fim().toEpochMilli() - p.inicio().toEpochMilli()).isEqualTo(60_000L);
    }

    @Test
    void periodoMaiorQueOMaximoEhRecusado() {
        assertThatThrownBy(() -> PeriodoDeFinalizacao.de(QUINTA, QUINTA.plusDays(31), null, null, SAO_PAULO, 31))
                .isInstanceOfSatisfying(FinalizacaoEmMassaException.class, e -> assertThat(e.codigo()).isEqualTo("PERIODO_EXCEDE_MAXIMO"));
        assertThat(PeriodoDeFinalizacao.de(QUINTA, QUINTA.plusDays(30), null, null, SAO_PAULO, 31)).isNotNull();
    }

    @Test
    void datasAusentesSaoRecusadas() {
        assertThatThrownBy(() -> periodo(null, SABADO, null, null))
                .isInstanceOfSatisfying(FinalizacaoEmMassaException.class, e -> assertThat(e.codigo()).isEqualTo("PERIODO_INVALIDO"));
        assertThatThrownBy(() -> periodo(QUINTA, null, null, null))
                .isInstanceOfSatisfying(FinalizacaoEmMassaException.class, e -> assertThat(e.codigo()).isEqualTo("PERIODO_INVALIDO"));
    }
}
