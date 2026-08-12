package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

/** Estado emitido pelo broker STOMP quando passa a aceitar ou deixa de aceitar tráfego. */
@Component
public class SaudeDoWebSocket implements ApplicationListener<BrokerAvailabilityEvent> {

    private final AtomicBoolean disponivel = new AtomicBoolean();

    @Override
    public void onApplicationEvent(BrokerAvailabilityEvent evento) {
        disponivel.set(evento.isBrokerAvailable());
    }

    public boolean disponivel() {
        return disponivel.get();
    }
}
