package com.synapse.crm.core.infrastructure.configuracao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.synapse.crm.core.domain.lead.TelefoneCanonico;

/** Liga a regra pura de telefone a configuracao desta instancia. */
@Configuration(proxyBeanMethods = false)
class TelefoneConfig {

    @Bean
    TelefoneCanonico telefoneCanonico(
            @Value("${synapse.telefone.ddi-padrao}") String ddiPadrao) {
        return new TelefoneCanonico(ddiPadrao);
    }
}
