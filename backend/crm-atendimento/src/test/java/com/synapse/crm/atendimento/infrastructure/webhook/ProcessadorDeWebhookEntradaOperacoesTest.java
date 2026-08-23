package com.synapse.crm.atendimento.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProcessadorDeWebhookEntradaOperacoesTest {

    @ParameterizedTest
    @MethodSource("comandosReset")
    void reconhece_reset_sem_diferenciar_caixa_ou_espacos(String texto) {
        assertThat(ProcessadorDeWebhookEntradaOperacoes.ehComandoReset(texto)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("naoComandosReset")
    void nao_confunde_texto_parecido_com_reset(String texto) {
        assertThat(ProcessadorDeWebhookEntradaOperacoes.ehComandoReset(texto)).isFalse();
    }

    private static Stream<Arguments> comandosReset() {
        return Stream.of(Arguments.of("#reset"), Arguments.of(" #RESET "), Arguments.of("\t#ReSeT\n"));
    }

    private static Stream<Arguments> naoComandosReset() {
        return Stream.of(Arguments.of((String) null), Arguments.of("reset"), Arguments.of("#resetar"), Arguments.of("texto #reset"));
    }
}
