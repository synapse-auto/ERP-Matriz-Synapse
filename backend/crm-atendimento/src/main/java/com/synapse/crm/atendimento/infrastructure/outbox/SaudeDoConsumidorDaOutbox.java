package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/** Heartbeat do consumidor da fila transacional que efetivamente envia mensagens ao canal. */
@Component
public class SaudeDoConsumidorDaOutbox {

    private final Clock relogio;
    private final AtomicReference<Instant> ultimoConsumoBemSucedido =
            new AtomicReference<>(Instant.EPOCH);

    public SaudeDoConsumidorDaOutbox(Clock relogio) {
        this.relogio = relogio;
    }

    void registrarConsumoBemSucedido() {
        ultimoConsumoBemSucedido.set(Instant.now(relogio));
    }

    public Instant ultimoConsumoBemSucedido() {
        return ultimoConsumoBemSucedido.get();
    }
}
