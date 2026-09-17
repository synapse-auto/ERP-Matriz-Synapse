package com.synapse.crm.app.foto;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;

/** Dispara a captura somente depois que a mensagem recebida foi commitada. */
@org.springframework.stereotype.Component
class CapturarFotoDePerfilAoReceberMensagemListener {

    private static final Logger log = LoggerFactory.getLogger(
            CapturarFotoDePerfilAoReceberMensagemListener.class);

    private final Executor executor;
    private final CapturarFotoDePerfilDoLeadService captura;

    CapturarFotoDePerfilAoReceberMensagemListener(
            @Qualifier("fotoDePerfilExecutor") Executor executor,
            CapturarFotoDePerfilDoLeadService captura) {
        this.executor = executor;
        this.captura = captura;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoReceber(EventoDeAtendimento.MensagemRecebida evento) {
        try {
            executor.execute(() -> captura.executar(evento.leadId()));
        } catch (RuntimeException rejeitada) {
            log.warn(
                    "Captura assíncrona de foto não enfileirada; leadId={}, tipoErro={}",
                    evento.leadId(),
                    rejeitada.getClass().getSimpleName());
        }
    }
}
