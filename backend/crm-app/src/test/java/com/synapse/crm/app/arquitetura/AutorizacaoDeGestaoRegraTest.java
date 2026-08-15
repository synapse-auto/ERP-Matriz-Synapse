package com.synapse.crm.app.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutorizacaoDeGestaoRegraTest {

    @Test
    @DisplayName("a protecao reprova expressao gerencial sem administrador")
    void expressaoGerencialSemAdministrador_eReprovada() {
        assertThat(ArquiteturaTest.autorizacaoGerencialIncluiAdministrador(
                        "hasAnyRole('GESTOR', 'SUBGESTOR')"))
                .isFalse();
    }

    @Test
    @DisplayName("a protecao aceita expressao gerencial com administrador")
    void expressaoGerencialComAdministrador_eAceita() {
        assertThat(ArquiteturaTest.autorizacaoGerencialIncluiAdministrador(
                        "hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')"))
                .isTrue();
    }
}
