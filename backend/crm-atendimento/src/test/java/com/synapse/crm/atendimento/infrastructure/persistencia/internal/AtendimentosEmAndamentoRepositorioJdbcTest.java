package com.synapse.crm.atendimento.infrastructure.persistencia.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AtendimentosEmAndamentoRepositorioJdbcTest {
    @Mock
    private JdbcTemplate chat;

    @AfterEach
    void encerrarTransacao() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void verificaExistenciaSemCarregarResponsavelOuUltimaMensagem() {
        UUID leadId = UUID.randomUUID();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(chat.queryForObject(anyString(), eq(Boolean.class), eq(leadId))).thenReturn(true);

        boolean existe = new AtendimentosEmAndamentoRepositorioJdbc(chat)
                .existeAtendimentoEmAndamento(leadId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(chat).queryForObject(sql.capture(), eq(Boolean.class), eq(leadId));
        assertThat(existe).isTrue();
        assertThat(sql.getValue())
                .contains("SELECT EXISTS")
                .contains("status = 'EM_ATENDIMENTO'")
                .doesNotContain("JOIN usuario")
                .doesNotContain("LATERAL")
                .doesNotContain("mensagem");
    }
}
