package com.synapse.crm.core.domain.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TelefoneCanonicoTest {

    private final TelefoneCanonico telefone = new TelefoneCanonico("55");

    @Test
    void formatosEquivalentesProduzemOMesmoTelefone() {
        assertThat(telefone.normalizar("+55 61 99999-9999"))
                .isEqualTo("5561999999999")
                .isEqualTo(telefone.normalizar("5561999999999"));
    }

    @Test
    void telefoneLocalRecebeODdiDaInstancia() {
        assertThat(telefone.normalizar("(61) 99999-9999")).isEqualTo("5561999999999");
    }

    @Test
    void numerosComDozeETrezeDigitosPassamIntactos() {
        assertThat(telefone.normalizar("123456789012")).isEqualTo("123456789012");
        assertThat(telefone.normalizar("1234567890123")).isEqualTo("1234567890123");
    }

    @Test
    void entradaAusenteContinuaValidaMasEntradaCurtaEhRecusada() {
        assertThat(telefone.normalizar(null)).isNull();
        assertThatThrownBy(() -> telefone.normalizar("1234"))
                .isInstanceOf(TelefoneInvalidoException.class)
                .hasMessageContaining("DDD e numero");
        assertThatThrownBy(() -> telefone.normalizar(" + - "))
                .isInstanceOf(TelefoneInvalidoException.class);
    }
}
