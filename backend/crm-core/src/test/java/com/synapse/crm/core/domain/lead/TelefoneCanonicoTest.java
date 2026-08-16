package com.synapse.crm.core.domain.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelefoneCanonicoTest {

    @Test
    void formatosEquivalentesProduzemOMesmoTelefone() {
        assertThat(TelefoneCanonico.normalizar("+55 61 99999-9999"))
                .isEqualTo("5561999999999")
                .isEqualTo(TelefoneCanonico.normalizar("5561999999999"));
    }

    @Test
    void entradaSemDigitosNaoViraUmaChaveVazia() {
        assertThat(TelefoneCanonico.normalizar(null)).isNull();
        assertThat(TelefoneCanonico.normalizar(" + - ")).isNull();
    }
}
