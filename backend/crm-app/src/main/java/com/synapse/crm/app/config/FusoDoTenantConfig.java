package com.synapse.crm.app.config;

import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O fuso civil da instancia, publicado como bean.
 *
 * <p>O {@code Clock} da aplicacao e {@code systemUTC()} — correto para instante, errado para "que
 * dia e hoje". Entre 21h e a meia-noite em Brasilia o dia em UTC ja e o seguinte: quem perguntasse
 * "quem faz aniversario hoje" nesse intervalo receberia a lista de amanha. Como a decisao de fuso
 * ja existe em {@code synapse.tenant.timezone} (e {@code HorarioComercialConfiguravel} ja a usa),
 * este bean apenas a expoe para quem precisa de data civil, em vez de cada modulo reler a
 * propriedade e escolher um default proprio.
 */
@Configuration
class FusoDoTenantConfig {

    @Bean
    ZoneId fusoDoTenant(SynapseProperties propriedades) {
        return ZoneId.of(propriedades.tenant().timezone());
    }
}
