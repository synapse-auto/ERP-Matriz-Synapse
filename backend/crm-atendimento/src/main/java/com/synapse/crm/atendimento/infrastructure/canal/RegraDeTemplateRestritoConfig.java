package com.synapse.crm.atendimento.infrastructure.canal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.synapse.crm.atendimento.domain.canal.RegraDeTemplateRestrito;

@Configuration
class RegraDeTemplateRestritoConfig {

    @Bean
    RegraDeTemplateRestrito regraDeTemplateRestrito(TemplatesProperties propriedades) {
        return new RegraDeTemplateRestrito(propriedades.termoRestrito());
    }
}
