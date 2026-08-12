package com.synapse.crm.app.saude.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.app.saude.application.MonitorarSaudeCriticaUseCase;

/** Detecta degradações com o processo vivo; o watchdog externo cobre queda total do host. */
@Component
@ConditionalOnProperty(
        prefix = "synapse.saude.critica",
        name = "monitoramento-habilitado",
        havingValue = "true",
        matchIfMissing = true)
class AgendadorDaSaudeCritica {

    private final MonitorarSaudeCriticaUseCase monitorar;

    AgendadorDaSaudeCritica(MonitorarSaudeCriticaUseCase monitorar) {
        this.monitorar = monitorar;
    }

    @Scheduled(fixedDelayString = "${synapse.saude.critica.intervalo-monitoramento}")
    void verificar() {
        monitorar.executar();
    }
}
