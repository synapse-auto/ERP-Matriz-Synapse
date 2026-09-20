package com.synapse.crm.automacaoconfig.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.automacaoconfig.application.fidelizacao.AniversariantesDoDiaRepositorio;
import com.synapse.crm.automacaoconfig.application.fidelizacao.AniversariantesDoDiaUseCase;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.fidelizacao.LeadAniversariante;

class AniversariantesDoDiaUseCaseTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LeadAniversariante MARIA =
            new LeadAniversariante(UUID.randomUUID(), "Maria Exemplo", "5561999990000");

    private final ObterConfiguracaoAutomacaoUseCase configuracao = mock(ObterConfiguracaoAutomacaoUseCase.class);
    private final AniversariantesDoDiaRepositorio repositorio = mock(AniversariantesDoDiaRepositorio.class);

    private AniversariantesDoDiaUseCase casoEm(String instante) {
        return new AniversariantesDoDiaUseCase(
                configuracao, repositorio, Clock.fixed(Instant.parse(instante), SAO_PAULO), SAO_PAULO);
    }

    private void configurar(String chave, String valor) {
        when(configuracao.executar(chave))
                .thenReturn(Optional.of(new ConfiguracaoAutomacao(
                        chave, valor, null, TipoConfiguracaoAutomacao.TEXT, null, null, null, null, Instant.EPOCH)));
    }

    @Test
    @DisplayName("desabilitado zera a lista no servidor, mesmo com aniversariante hoje")
    void desabilitadoNaoConsultaNemDevolveLeads() {
        configurar("fidelizacao.aniversario.habilitado", "false");
        configurar("fidelizacao.aniversario.mensagem", "Feliz aniversário, [nome]!");

        var resultado = casoEm("2026-05-04T12:00:00Z").executar();

        assertThat(resultado.habilitado()).isFalse();
        assertThat(resultado.leads()).isEmpty();
        assertThat(resultado.mensagem()).isEqualTo("Feliz aniversário, [nome]!");
        verify(repositorio, never()).doDia(any());
    }

    @Test
    @DisplayName("habilitado devolve os leads do dia e mes de hoje")
    void habilitadoDevolveOsLeadsDoDia() {
        configurar("fidelizacao.aniversario.habilitado", "true");
        configurar("fidelizacao.aniversario.mensagem", "Feliz aniversário, [nome]!");
        when(repositorio.doDia(MonthDay.of(5, 4))).thenReturn(List.of(MARIA));

        var resultado = casoEm("2026-05-04T12:00:00Z").executar();

        assertThat(resultado.habilitado()).isTrue();
        assertThat(resultado.leads()).containsExactly(MARIA);
    }

    @Test
    @DisplayName("chave ausente e tratada como desligada, sem erro")
    void chaveAusenteNaoDisparaNada() {
        when(configuracao.executar(any())).thenReturn(Optional.empty());

        var resultado = casoEm("2026-05-04T12:00:00Z").executar();

        assertThat(resultado.habilitado()).isFalse();
        assertThat(resultado.mensagem()).isNull();
        assertThat(resultado.leads()).isEmpty();
    }

    @Test
    @DisplayName("o dia consultado e o do fuso da instancia, nao o de UTC")
    void consultaODiaCivilDoTenant() {
        // 05/05 as 01:00Z ainda e 04/05 as 22:00 em Sao Paulo.
        configurar("fidelizacao.aniversario.habilitado", "true");
        configurar("fidelizacao.aniversario.mensagem", "Feliz aniversário, [nome]!");
        when(repositorio.doDia(MonthDay.of(5, 4))).thenReturn(List.of(MARIA));

        var resultado = casoEm("2026-05-05T01:00:00Z").executar();

        assertThat(resultado.leads()).containsExactly(MARIA);
        verify(repositorio).doDia(MonthDay.of(5, 4));
    }
}
