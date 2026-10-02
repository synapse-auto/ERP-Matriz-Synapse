package com.synapse.crm.campanhas.infrastructure;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.campanhas.application.ExecutarCicloDeCampanhasUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Dispara o ciclo do motor de campanhas. So agenda e declara o contexto de servico; a logica mora no caso de
 * uso, chamado por fora do proprio bean (pelo proxy), como o publicador da outbox faz.
 *
 * <p>Um ciclo que ainda esta rodando nao se sobrepoe ao seguinte: {@code fixedDelay} espera o termino. E, de
 * toda forma, ha o lease por campanha para o caso de duas instancias.
 */
@Component
public class AgendadorDeCampanhas {

    private final ExecutarCicloDeCampanhasUseCase ciclo;

    AgendadorDeCampanhas(ExecutarCicloDeCampanhasUseCase ciclo) {
        this.ciclo = ciclo;
    }

    @Scheduled(fixedDelayString = "${synapse.campanhas.intervalo-ms:10000}")
    public void executarCiclo() {
        ContextoDeServico.executarComo("campanhas-ciclo", ciclo::executar);
    }
}
