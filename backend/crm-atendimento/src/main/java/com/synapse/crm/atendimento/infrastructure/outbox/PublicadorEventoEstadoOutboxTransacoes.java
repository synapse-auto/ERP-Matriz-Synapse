package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Transações curtas do publisher; nenhuma chamada Redis acontece com conexão SQL aberta. */
@Component
class PublicadorEventoEstadoOutboxTransacoes {

    private final OutboxEventoEstadoRepositorioJdbc outbox;
    private final OutboxProperties propriedades;

    PublicadorEventoEstadoOutboxTransacoes(
            OutboxEventoEstadoRepositorioJdbc outbox, OutboxProperties propriedades) {
        this.outbox = outbox;
        this.propriedades = propriedades;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public List<OutboxEventoEstadoRepositorioJdbc.Pendente> reservar(Instant agora) {
        return outbox.reservar(
                propriedades.lote(), agora, agora.plus(propriedades.reservaExpiracao()));
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void registrarSucesso(OutboxEventoEstadoRepositorioJdbc.Pendente pendente, Instant agora) {
        outbox.marcarPublicado(pendente.outboxId(), agora);
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean registrarFalha(
            OutboxEventoEstadoRepositorioJdbc.Pendente pendente, Instant agora, String erro) {
        if (pendente.tentativas() + 1 >= propriedades.maximoDeTentativas()) {
            outbox.esgotar(pendente.outboxId(), agora, erro);
            return true;
        }
        outbox.reagendar(
                pendente.outboxId(),
                agora.plus(propriedades.esperaApos(pendente.tentativas())),
                erro);
        return false;
    }
}
