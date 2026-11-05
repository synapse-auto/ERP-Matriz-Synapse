package com.synapse.crm.atendimento.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.synapse.crm.core.domain.evento.DadosCadastraisDoLeadAlterados;

class SinalizadorDeFichaDoLeadTest {
    @Test void semTransacaoNaoAbreConexaoSemRls() {
        var geral = mock(DataSource.class); var chat = mock(DataSource.class);
        var eventos = mock(ApplicationEventPublisher.class);
        var sinal = new SinalizadorDeFichaDoLead(geral,chat,eventos,Clock.systemUTC());
        assertThatThrownBy(() -> sinal.sinalizar(new DadosCadastraisDoLeadAlterados(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(geral,chat,eventos);
    }
    @Test void doisPoolsNaoEscolhemTransacaoArbitrariamente() {
        var geral = mock(DataSource.class); var chat = mock(DataSource.class);
        var eventos = mock(ApplicationEventPublisher.class);
        try {
            TransactionSynchronizationManager.bindResource(geral,new Object());
            TransactionSynchronizationManager.bindResource(chat,new Object());
            TransactionSynchronizationManager.setActualTransactionActive(true);
            var sinal = new SinalizadorDeFichaDoLead(geral,chat,eventos,Clock.systemUTC());
            assertThatThrownBy(() -> sinal.sinalizar(new DadosCadastraisDoLeadAlterados(UUID.randomUUID())))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("pools concorrentes");
            verifyNoInteractions(eventos);
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(geral);
            TransactionSynchronizationManager.unbindResourceIfPossible(chat);
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
