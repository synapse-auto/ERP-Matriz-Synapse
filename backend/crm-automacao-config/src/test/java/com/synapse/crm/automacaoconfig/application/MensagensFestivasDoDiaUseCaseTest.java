package com.synapse.crm.automacaoconfig.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.automacaoconfig.application.festivas.MensagemFestivaRepositorio;
import com.synapse.crm.automacaoconfig.application.festivas.MensagensFestivasDoDiaUseCase;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;

/** Relogio fixo: o teste prova a regra de mes e dia, nao a data em que o CI rodou. */
class MensagensFestivasDoDiaUseCaseTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final MensagemFestivaRepositorio repositorio = mock(MensagemFestivaRepositorio.class);

    private MensagensFestivasDoDiaUseCase casoEm(String instante) {
        return new MensagensFestivasDoDiaUseCase(
                repositorio, Clock.fixed(Instant.parse(instante), SAO_PAULO), SAO_PAULO);
    }

    private static MensagemFestiva festiva(String titulo, LocalDate data, boolean ativo) {
        return new MensagemFestiva(UUID.randomUUID(), titulo, "🎉", data, "Mensagem de " + titulo, ativo);
    }

    @Test
    @DisplayName("data cadastrada em outro ano continua valendo: o ano e ignorado")
    void ignoraOAnoDoCadastro() {
        MensagemFestiva natalDeOutroAno = festiva("Natal", LocalDate.of(2021, 12, 25), true);
        when(repositorio.listarTodas()).thenReturn(List.of(natalDeOutroAno));

        List<MensagemFestiva> hoje = casoEm("2026-12-25T12:00:00Z").executar();

        assertThat(hoje).containsExactly(natalDeOutroAno);
    }

    @Test
    @DisplayName("inativa e de outro dia ficam de fora; duplicada no mesmo dia sai inteira")
    void filtraPorAtivoEPorDia() {
        MensagemFestiva ativaDeHoje = festiva("Natal", LocalDate.of(2026, 12, 25), true);
        MensagemFestiva duplicadaDeHoje = festiva("Natal da equipe", LocalDate.of(2019, 12, 25), true);
        MensagemFestiva inativaDeHoje = festiva("Natal desligado", LocalDate.of(2026, 12, 25), false);
        MensagemFestiva deOutroDia = festiva("Ano novo", LocalDate.of(2026, 1, 1), true);
        when(repositorio.listarTodas())
                .thenReturn(List.of(ativaDeHoje, duplicadaDeHoje, inativaDeHoje, deOutroDia));

        List<MensagemFestiva> hoje = casoEm("2026-12-25T12:00:00Z").executar();

        assertThat(hoje).containsExactly(ativaDeHoje, duplicadaDeHoje);
    }

    @Test
    @DisplayName("dia sem data festiva devolve lista vazia")
    void diaSemDataFestivaDevolveVazio() {
        when(repositorio.listarTodas()).thenReturn(List.of(festiva("Natal", LocalDate.of(2026, 12, 25), true)));

        assertThat(casoEm("2026-07-04T12:00:00Z").executar()).isEmpty();
    }

    @Test
    @DisplayName("o dia e o do fuso da instancia, nao o de UTC")
    void usaODiaCivilDoTenant() {
        // 26/12 as 01:00Z ainda e 25/12 as 22:00 em Sao Paulo: quem pergunta a noite tem de
        // receber a data festiva de hoje, nao a de amanha.
        MensagemFestiva natal = festiva("Natal", LocalDate.of(2026, 12, 25), true);
        when(repositorio.listarTodas()).thenReturn(List.of(natal));

        assertThat(casoEm("2026-12-26T01:00:00Z").executar()).containsExactly(natal);
    }
}
