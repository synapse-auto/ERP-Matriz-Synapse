package com.synapse.crm.atendimento.infrastructure.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Ponto de entrada real do worker durável de sinais de estado. */
@Component
public class PublicadorEventoEstadoOutbox {

    private final PublicadorEventoEstadoOutboxOperacoes operacoes;

    PublicadorEventoEstadoOutbox(PublicadorEventoEstadoOutboxOperacoes operacoes) {
        this.operacoes = operacoes;
    }

    @Scheduled(fixedDelayString = "${synapse.tempo-real.outbox.intervalo-ms:250}")
    public void publicarPendentes() {
        ContextoDeServico.executarComo("publicador-outbox-estado-atendimento", operacoes::rodada);
    }
}
