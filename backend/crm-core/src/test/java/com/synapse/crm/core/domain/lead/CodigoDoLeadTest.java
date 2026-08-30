package com.synapse.crm.core.domain.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CodigoDoLeadTest {

    @Test
    void vazioViraNulo() {
        assertThat(CodigoDoLead.normalizar("")).isNull();
        assertThat(CodigoDoLead.normalizar("   ")).isNull();
    }

    @Test
    void preservaZerosAEsquerda() {
        assertThat(CodigoDoLead.normalizar("00421")).isEqualTo("00421");
    }

    @Test
    void recusaLetra() {
        assertThatThrownBy(() -> CodigoDoLead.normalizar("12a"))
                .isInstanceOf(CodigoInvalidoException.class);
    }

    @Test
    void recusaAcimaDoTamanho() {
        assertThatThrownBy(() -> CodigoDoLead.normalizar("1".repeat(21)))
                .isInstanceOf(CodigoInvalidoException.class);
    }
}
