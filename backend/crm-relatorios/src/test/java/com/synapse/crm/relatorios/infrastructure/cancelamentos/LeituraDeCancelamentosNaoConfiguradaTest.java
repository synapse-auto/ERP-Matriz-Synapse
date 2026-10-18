package com.synapse.crm.relatorios.infrastructure.cancelamentos;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.synapse.crm.relatorios.application.cancelamentos.FonteCancelamentosException;

class LeituraDeCancelamentosNaoConfiguradaTest {
    @Test void producaoNaoInventaListaVaziaOuConsultaBancoExterno() {
        var fonte = new LeituraDeCancelamentosNaoConfigurada();
        assertThatThrownBy(() -> fonte.listar(null,null)).isInstanceOf(FonteCancelamentosException.class)
            .hasMessage("INTEGRACAO_NAO_CONFIGURADA");
        assertThatThrownBy(() -> fonte.detalhe("1",null)).isInstanceOf(FonteCancelamentosException.class)
            .hasMessage("INTEGRACAO_NAO_CONFIGURADA");
    }
}
