package com.synapse.crm.atendimento.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.ConnectionHolder;
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
    @Test void doisPoolsNaoEscolhemTransacaoArbitrariamente() throws SQLException {
        var geral = mock(DataSource.class); var chat = mock(DataSource.class);
        var eventos = mock(ApplicationEventPublisher.class);
        try {
            TransactionSynchronizationManager.bindResource(geral,vinculo(false));
            TransactionSynchronizationManager.bindResource(chat,vinculo(false));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            var sinal = new SinalizadorDeFichaDoLead(geral,chat,eventos,Clock.systemUTC());
            assertThatThrownBy(() -> sinal.sinalizar(new DadosCadastraisDoLeadAlterados(UUID.randomUUID())))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("exatamente uma");
            verifyNoInteractions(eventos);
        } finally {
            TransactionSynchronizationManager.unbindResourceIfPossible(geral);
            TransactionSynchronizationManager.unbindResourceIfPossible(chat);
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void conexaoPassivaDoOutroPoolNaoMudaATransacao(boolean geralAtivo) throws SQLException {
        var geral = mock(DataSource.class); var chat = mock(DataSource.class);
        try {
            TransactionSynchronizationManager.bindResource(geral,vinculo(!geralAtivo));
            TransactionSynchronizationManager.bindResource(chat,vinculo(geralAtivo));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThat(ConexaoDaTransacaoAtual.exigir(geral,chat)).isSameAs(geralAtivo ? geral : chat);
            verifyNoInteractions(geral,chat); // nenhuma nova conexao e solicitada.
        } finally { limpar(geral,chat); }
    }

    @Test void doisVinculosPassivosNaoAutorizamEscrita() throws SQLException {
        var geral = mock(DataSource.class); var chat = mock(DataSource.class);
        try {
            TransactionSynchronizationManager.bindResource(geral,vinculo(true));
            TransactionSynchronizationManager.bindResource(chat,vinculo(true));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThatThrownBy(() -> ConexaoDaTransacaoAtual.exigir(geral,chat)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(geral,chat);
        } finally { limpar(geral,chat); }
    }

    private static ConnectionHolder vinculo(boolean autoCommit) throws SQLException {
        var conexao = mock(Connection.class);
        when(conexao.getAutoCommit()).thenReturn(autoCommit);
        return new ConnectionHolder(conexao);
    }

    private static void limpar(DataSource geral, DataSource chat) {
        TransactionSynchronizationManager.unbindResourceIfPossible(geral);
        TransactionSynchronizationManager.unbindResourceIfPossible(chat);
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
