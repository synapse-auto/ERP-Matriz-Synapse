package com.synapse.crm.core.infrastructure.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.synapse.crm.core.domain.lead.TelefoneCanonico;

@SpringJUnitConfig(TelefoneConfig.class)
@TestPropertySource(properties = "synapse.telefone.ddi-padrao=54")
class TelefoneConfigTest {

    @Autowired
    private TelefoneCanonico telefone;

    @Test
    void propriedadeDaInstanciaDefineOutroDdiSemAlterarCodigo() {
        assertThat(telefone.normalizar("(11) 99999-9999")).isEqualTo("5411999999999");
    }
}
