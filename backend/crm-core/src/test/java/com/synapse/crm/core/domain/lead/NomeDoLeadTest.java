package com.synapse.crm.core.domain.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NomeDoLeadTest {

    @Test
    void trimPreservaOTexto() {
        assertThat(NomeDoLead.normalizar("  Maria Silva  ")).isEqualTo("Maria Silva");
    }

    @Test
    void recusaVazio() {
        assertThatThrownBy(() -> NomeDoLead.normalizar(""))
                .isInstanceOf(NomeInvalidoException.class);
        assertThatThrownBy(() -> NomeDoLead.normalizar("   "))
                .isInstanceOf(NomeInvalidoException.class);
    }

    @Test
    void recusaAcimaDoTamanho() {
        assertThatThrownBy(() -> NomeDoLead.normalizar("a".repeat(151)))
                .isInstanceOf(NomeInvalidoException.class);
    }
}
