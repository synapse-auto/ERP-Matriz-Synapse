package com.synapse.crm.atendimento.infrastructure.avaliacao;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.OutboxDeAvaliacao;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Somente banco; as chamadas de rede ficam fora destes proxies transacionais. */
@Component
class AvaliacaoOutboxTransacoes {
    private final OutboxDeAvaliacao outbox;
    private final AvaliacaoWebhookProperties config;

    AvaliacaoOutboxTransacoes(OutboxDeAvaliacao outbox, AvaliacaoWebhookProperties config) {
        this.outbox = outbox;
        this.config = config;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Optional<OutboxDeAvaliacao.Reserva> reservar(Instant agora) {
        return outbox.reservar(1, config.maximoTentativas(), agora, agora.plus(config.reservaExpiracao()))
                .stream().findFirst();
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean registrar(OutboxDeAvaliacao.Reserva reserva, AvaliacaoWebhookHttp.Resultado resultado, Instant agora) {
        if (resultado.sucesso()) {
            return outbox.concluir(reserva, agora);
        }
        return outbox.falhar(reserva, agora, agora.plus(config.esperaApos(reserva.tentativas())),
                resultado.classe(), resultado.permanente() || reserva.tentativas() >= config.maximoTentativas());
    }
}
