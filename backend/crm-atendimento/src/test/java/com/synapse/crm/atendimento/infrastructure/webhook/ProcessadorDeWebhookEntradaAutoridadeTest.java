package com.synapse.crm.atendimento.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.synapse.crm.equipe.infrastructure.seguranca.ContextoDeServicoSpringSecurity;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

class ProcessadorDeWebhookEntradaAutoridadeTest {

    @BeforeEach
    void prepararPonte() {
        SecurityContextHolder.clearContext();
        new ContextoDeServicoSpringSecurity();
    }

    @AfterEach
    void limparPonte() {
        SecurityContextHolder.clearContext();
        ContextoDeServico.instalarPonteDeAutoridade(ContextoDeServico.PonteDeAutoridade.NAO_INSTALADA);
    }

    @Test
    void caminhoAgendado_carregaRoleServicoDuranteRodadaEFechaEscopo() {
        ProcessadorDeWebhookEntradaOperacoes operacoes = mock(ProcessadorDeWebhookEntradaOperacoes.class);
        AtomicReference<Authentication> duranteRodada = new AtomicReference<>();
        doAnswer(invocacao -> {
            duranteRodada.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(operacoes).rodada();

        new ProcessadorDeWebhookEntrada(operacoes).processarPendentes();

        assertThat(duranteRodada.get()).isNotNull();
        assertThat(duranteRodada.get().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_SERVICO");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
