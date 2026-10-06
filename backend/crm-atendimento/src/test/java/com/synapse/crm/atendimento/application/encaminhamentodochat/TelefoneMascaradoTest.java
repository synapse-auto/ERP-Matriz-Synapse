package com.synapse.crm.atendimento.application.encaminhamentodochat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelefoneMascaradoTest {

    @Test
    void mantemPrefixoEQuatroFinais() {
        assertThat(TelefoneMascarado.de("5561999991234")).isEqualTo("5561*****1234");
        assertThat(TelefoneMascarado.de("+55 (61) 99999-1234")).isEqualTo("5561*****1234");
    }

    @Test
    void numeroCurtoMostraSoOsDoisFinais() {
        assertThat(TelefoneMascarado.de("99991234")).isEqualTo("******34");
        assertThat(TelefoneMascarado.de("12")).isEqualTo("**");
    }

    @Test
    void nuloOuSemDigitos() {
        assertThat(TelefoneMascarado.de(null)).isEmpty();
        assertThat(TelefoneMascarado.de("sem numero")).isEmpty();
    }

    @Test
    void nuncaDevolveONumeroInteiro() {
        String telefone = "5561999991234";
        assertThat(TelefoneMascarado.de(telefone)).isNotEqualTo(telefone).contains("*");
    }
}
