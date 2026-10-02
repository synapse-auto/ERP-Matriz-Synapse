package com.synapse.crm.campanhas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.campanhas.domain.PlanoDeLimite.DiaProjetado;
import com.synapse.crm.campanhas.domain.PlanoDeLimite.Projecao;
import com.synapse.crm.campanhas.domain.PlanoDeLimite.Rampa;

class PlanoDeLimiteTest {

    /** 05/10/2026 e uma segunda-feira. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 5);

    private static final JanelaDeEnvio JANELA_COMERCIAL =
            new JanelaDeEnvio(LocalTime.of(9, 0), LocalTime.of(18, 0), DiasDaSemana.DIAS_UTEIS);

    @Test
    @DisplayName("sem rampa o limite e o da campanha, mas nunca passa do teto da instancia")
    void limiteSemRampa() {
        assertThat(PlanoDeLimite.limiteDoDia(100, null, 0, 200)).isEqualTo(100);
        assertThat(PlanoDeLimite.limiteDoDia(100, null, 30, 200)).isEqualTo(100);
        assertThat(PlanoDeLimite.limiteDoDia(500, null, 0, 200)).isEqualTo(200);
    }

    @Test
    @DisplayName("a rampa soma o aumento por dia ate o teto da rampa e o teto da instancia continua mandando")
    void limiteComRampa() {
        Rampa rampa = new Rampa(50, 300);

        assertThat(PlanoDeLimite.limiteDoDia(100, rampa, 0, 1000)).isEqualTo(100);
        assertThat(PlanoDeLimite.limiteDoDia(100, rampa, 1, 1000)).isEqualTo(150);
        assertThat(PlanoDeLimite.limiteDoDia(100, rampa, 4, 1000)).isEqualTo(300);
        assertThat(PlanoDeLimite.limiteDoDia(100, rampa, 40, 1000)).isEqualTo(300);
        assertThat(PlanoDeLimite.limiteDoDia(100, rampa, 40, 250)).isEqualTo(250);
    }

    @Test
    @DisplayName("rampa exige aumento e teto positivos")
    void rampaInvalida() {
        assertThatThrownBy(() -> new Rampa(0, 100)).isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> new Rampa(10, 0)).isInstanceOf(CampanhaInvalidaException.class);
    }

    @Test
    @DisplayName("a capacidade fisica do dia e o ritmo por minuto vezes os minutos da janela")
    void capacidadeDoDia() {
        assertThat(PlanoDeLimite.capacidadeDoDia(20, JANELA_COMERCIAL)).isEqualTo(20 * 540);
    }

    @Test
    @DisplayName("projecao: 1000 contatos a 100 por dia, so dias uteis, terminam em duas semanas")
    void projecaoSoEmDiasUteis() {
        Projecao projecao = PlanoDeLimite.projetar(1000, SEGUNDA, SEGUNDA, JANELA_COMERCIAL, 100, null, 20, 200);

        assertThat(projecao.completa()).isTrue();
        assertThat(projecao.dias()).hasSize(10);
        assertThat(projecao.dias()).extracting(DiaProjetado::mensagens).containsOnly(100);
        assertThat(projecao.dias()).extracting(DiaProjetado::dia).noneMatch(dia ->
                dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY);
        assertThat(projecao.terminoEstimado()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    @DisplayName("projecao: o ritmo por minuto limita o dia mesmo com limite diario maior")
    void projecaoLimitadaPeloRitmo() {
        JanelaDeEnvio umaHora = new JanelaDeEnvio(
                LocalTime.of(9, 0), LocalTime.of(10, 0), DiasDaSemana.de(Set.of(DayOfWeek.MONDAY)));

        Projecao projecao = PlanoDeLimite.projetar(100, SEGUNDA, SEGUNDA, umaHora, 100, null, 1, 200);

        assertThat(projecao.dias().get(0).limiteDoDia()).isEqualTo(60);
        assertThat(projecao.dias().get(0).mensagens()).isEqualTo(60);
        assertThat(projecao.dias().get(1).dia()).isEqualTo(SEGUNDA.plusDays(7));
        assertThat(projecao.dias().get(1).mensagens()).isEqualTo(40);
    }

    @Test
    @DisplayName("projecao com rampa cresce dia a dia")
    void projecaoComRampa() {
        Projecao projecao = PlanoDeLimite.projetar(
                600, SEGUNDA, SEGUNDA, JANELA_COMERCIAL, 100, new Rampa(100, 300), 20, 1000);

        assertThat(projecao.dias()).extracting(DiaProjetado::mensagens).containsExactly(100, 200, 300);
    }

    @Test
    @DisplayName("projecao que nao fecha em um ano e marcada incompleta, sem data de termino")
    void projecaoIncompleta() {
        Projecao projecao = PlanoDeLimite.projetar(1_000_000, SEGUNDA, SEGUNDA, JANELA_COMERCIAL, 1, null, 20, 200);

        assertThat(projecao.completa()).isFalse();
        assertThat(projecao.terminoEstimado()).isNull();
    }

    @Test
    @DisplayName("sem destinatarios nao ha dias projetados")
    void projecaoVazia() {
        Projecao projecao = PlanoDeLimite.projetar(0, SEGUNDA, SEGUNDA, JANELA_COMERCIAL, 100, null, 20, 200);

        assertThat(projecao.dias()).isEmpty();
        assertThat(projecao.completa()).isTrue();
        assertThat(projecao.terminoEstimado()).isNull();
    }
}
