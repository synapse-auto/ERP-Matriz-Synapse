package com.synapse.crm.app.foto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;

class CapturarFotoDePerfilAoReceberMensagemListenerTest {

    @Test
    void enfileiraCapturaSemExecutarNoThreadDaMensagem() {
        Executor executor = mock(Executor.class);
        CapturarFotoDePerfilDoLeadService captura = mock(CapturarFotoDePerfilDoLeadService.class);
        CapturarFotoDePerfilAoReceberMensagemListener listener =
                new CapturarFotoDePerfilAoReceberMensagemListener(executor, captura);
        UUID leadId = UUID.randomUUID();

        listener.aoReceber(new EventoDeAtendimento.MensagemRecebida(
                leadId, UUID.randomUUID(), UUID.randomUUID(), true, Instant.now()));

        var tarefa = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(tarefa.capture());
        verifyNoInteractions(captura);
        tarefa.getValue().run();
        verify(captura).executar(leadId);
    }

    @Test
    void listenerSoRodaDepoisDoCommit() throws Exception {
        var metodo = CapturarFotoDePerfilAoReceberMensagemListener.class
                .getDeclaredMethod("aoReceber", EventoDeAtendimento.MensagemRecebida.class);
        var anotacao = metodo.getAnnotation(TransactionalEventListener.class);

        assertThat(anotacao).isNotNull();
        assertThat(anotacao.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
