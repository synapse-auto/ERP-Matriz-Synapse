package com.synapse.crm.app.saude.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.synapse.crm.app.saude.application.HorarioComercialDoCliente;
import com.synapse.crm.app.saude.application.MonitorarSaudeCriticaUseCase;
import com.synapse.crm.app.saude.application.PoliticaDeMonitoramento;
import com.synapse.crm.app.saude.application.PublicadorDeAlertaDeSaude;
import com.synapse.crm.app.saude.application.VerificarSaudeCriticaUseCase;

@Configuration
class ConfiguracaoDaSaudeCritica {

    @Bean
    PoliticaDeMonitoramento politicaDeMonitoramento(SaudeCriticaProperties propriedades) {
        return new PoliticaDeMonitoramento(propriedades.falhasConsecutivasParaAlertar());
    }

    @Bean
    MonitorarSaudeCriticaUseCase monitorarSaudeCriticaUseCase(
            VerificarSaudeCriticaUseCase verificar,
            PublicadorDeAlertaDeSaude publicador,
            HorarioComercialDoCliente horario,
            PoliticaDeMonitoramento politica) {
        return new MonitorarSaudeCriticaUseCase(verificar, publicador, horario, politica);
    }
}
