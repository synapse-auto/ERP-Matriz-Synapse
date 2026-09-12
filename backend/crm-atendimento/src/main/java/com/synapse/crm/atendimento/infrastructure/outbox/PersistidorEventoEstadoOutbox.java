package com.synapse.crm.atendimento.infrastructure.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;

/** Converte o evento de domínio em outbox antes do commit da transação que mudou o atendimento. */
@Component
class PersistidorEventoEstadoOutbox {

    private final OutboxEventoEstadoRepositorioJdbc outbox;

    PersistidorEventoEstadoOutbox(OutboxEventoEstadoRepositorioJdbc outbox) {
        this.outbox = outbox;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void persistir(EventoCanonicoDeAtendimento evento) {
        outbox.enfileirar(evento);
    }
}
