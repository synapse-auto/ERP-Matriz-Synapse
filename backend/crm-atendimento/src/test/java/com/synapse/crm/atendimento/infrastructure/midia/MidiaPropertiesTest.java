package com.synapse.crm.atendimento.infrastructure.midia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MidiaPropertiesTest {

    @Test
    void defaultDeExpiracaoLeitura_eUmaHora() {
        MidiaProperties propriedades = new MidiaProperties(null, null, null, null, null, null);
        assertThat(propriedades.expiracaoLeitura()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void expiracaoLeituraInformada_naoESubstituidaPeloDefault() {
        MidiaProperties propriedades =
                new MidiaProperties(null, null, null, null, null, Duration.ofMillis(500));
        assertThat(propriedades.expiracaoLeitura()).isEqualTo(Duration.ofMillis(500));
    }
}
