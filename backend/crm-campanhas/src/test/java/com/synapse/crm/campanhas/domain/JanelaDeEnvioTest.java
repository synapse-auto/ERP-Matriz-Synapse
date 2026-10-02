package com.synapse.crm.campanhas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JanelaDeEnvioTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final JanelaDeEnvio JANELA =
            new JanelaDeEnvio(LocalTime.of(9, 0), LocalTime.of(18, 0), DiasDaSemana.DIAS_UTEIS);

    @Test
    @DisplayName("aberta em dia util dentro do horario; o horario final e exclusivo")
    void abertaEm() {
        assertThat(JANELA.abertaEm(em(5, 9, 0))).isTrue();
        assertThat(JANELA.abertaEm(em(5, 17, 59))).isTrue();
        assertThat(JANELA.abertaEm(em(5, 18, 0))).isFalse();
        assertThat(JANELA.abertaEm(em(5, 8, 59))).isFalse();
    }

    @Test
    @DisplayName("fechada no fim de semana mesmo dentro do horario")
    void fechadaNoFimDeSemana() {
        assertThat(JANELA.abertaEm(em(10, 10, 0))).isFalse();
        assertThat(JANELA.abertaEm(em(11, 10, 0))).isFalse();
    }

    @Test
    @DisplayName("proxima abertura: hoje mais tarde, amanha ou segunda-feira")
    void proximaAbertura() {
        assertThat(JANELA.proximaAberturaApos(em(5, 8, 0))).isEqualTo(em(5, 9, 0));
        assertThat(JANELA.proximaAberturaApos(em(5, 10, 0))).isEqualTo(em(6, 9, 0));
        assertThat(JANELA.proximaAberturaApos(em(9, 19, 0))).isEqualTo(em(12, 9, 0));
        assertThat(JANELA.proximaAberturaApos(em(10, 10, 0))).isEqualTo(em(12, 9, 0));
    }

    @Test
    @DisplayName("horario inicial depois do final e recusado")
    void janelaInvertida() {
        assertThatThrownBy(() -> new JanelaDeEnvio(LocalTime.of(18, 0), LocalTime.of(9, 0), DiasDaSemana.DIAS_UTEIS))
                .isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> new JanelaDeEnvio(LocalTime.of(9, 0), LocalTime.of(9, 0), DiasDaSemana.DIAS_UTEIS))
                .isInstanceOf(CampanhaInvalidaException.class);
    }

    @Test
    @DisplayName("mascara de dias: ida e volta, e nenhum dia e recusado")
    void diasDaSemana() {
        DiasDaSemana dias = DiasDaSemana.de(Set.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY));

        assertThat(dias.mascara()).isEqualTo(0b1000001);
        assertThat(dias.conjunto()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
        assertThat(DiasDaSemana.DIAS_UTEIS.mascara()).isEqualTo(31);
        assertThatThrownBy(() -> DiasDaSemana.de(Set.of())).isInstanceOf(CampanhaInvalidaException.class);
    }

    /** Outubro/2026: dia 5 e segunda-feira. */
    private static ZonedDateTime em(int dia, int hora, int minuto) {
        return ZonedDateTime.of(2026, 10, dia, hora, minuto, 0, 0, BRASILIA);
    }
}
