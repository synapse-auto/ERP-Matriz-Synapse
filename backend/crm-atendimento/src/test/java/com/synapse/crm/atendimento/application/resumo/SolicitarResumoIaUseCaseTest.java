package com.synapse.crm.atendimento.application.resumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;

@ExtendWith(MockitoExtension.class)
class SolicitarResumoIaUseCaseTest {
    private static final UUID ATENDIMENTO = UUID.randomUUID();
    private static final UUID LEAD = UUID.randomUUID();
    private static final UUID SOLICITACAO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-09-17T18:00:00Z");

    @Mock private AtendimentoRepositorio atendimentos;
    @Mock private SolicitacaoResumoIaRepositorio solicitacoes;
    @Mock private ResumoIaAutomacaoGateway automacao;
    @Mock private Outbox outbox;
    @Mock private ApplicationEventPublisher eventos;

    private SolicitarResumoIaUseCase caso;
    private Atendimento atendimento;

    @BeforeEach
    void preparar() {
        atendimento = new Atendimento(
                ATENDIMENTO,
                LEAD,
                null,
                null,
                UUID.randomUUID(),
                StatusAtendimento.EM_ATENDIMENTO,
                AGORA.minusSeconds(60),
                null);
        caso = new SolicitarResumoIaUseCase(
                atendimentos,
                solicitacoes,
                automacao,
                outbox,
                eventos,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        when(automacao.configurado()).thenReturn(true);
        when(atendimentos.porId(ATENDIMENTO)).thenReturn(Optional.of(atendimento));
    }

    @Test
    void criaUmCicloEEnfileiraSemEnviarNoRequest() {
        var pendente = solicitacao(SolicitacaoResumoIaRepositorio.Status.PENDENTE);
        when(solicitacoes.porId(SOLICITACAO)).thenReturn(Optional.empty(), Optional.of(pendente));
        when(solicitacoes.ultimaDoAtendimento(ATENDIMENTO)).thenReturn(Optional.empty());

        var resultado = caso.executar(ATENDIMENTO, SOLICITACAO);

        assertThat(resultado).isEqualTo(pendente);
        verify(solicitacoes).criar(SOLICITACAO, LEAD, ATENDIMENTO, AGORA);
        verify(outbox).enfileirarSolicitacaoResumoIa(SOLICITACAO, LEAD, ATENDIMENTO, AGORA);
        verify(automacao, never()).enviar(any(), any(), any(), any());
    }

    @Test
    void replayDaMesmaChaveNaoCriaOutroCiclo() {
        var existente = solicitacao(SolicitacaoResumoIaRepositorio.Status.PROCESSANDO);
        when(solicitacoes.porId(SOLICITACAO)).thenReturn(Optional.of(existente));

        assertThat(caso.executar(ATENDIMENTO, SOLICITACAO)).isEqualTo(existente);
        verify(solicitacoes, never()).criar(any(), any(), any(), any());
        verify(outbox, never()).enfileirarSolicitacaoResumoIa(any(), any(), any(), any());
    }

    @Test
    void mesmaChaveParaOutroAtendimentoERejeitada() {
        var existente = new SolicitacaoResumoIaRepositorio.Solicitacao(
                SOLICITACAO,
                UUID.randomUUID(),
                UUID.randomUUID(),
                SolicitacaoResumoIaRepositorio.Status.PENDENTE,
                AGORA,
                AGORA,
                null,
                null);
        when(solicitacoes.porId(SOLICITACAO)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> caso.executar(ATENDIMENTO, SOLICITACAO))
                .isInstanceOf(SolicitarResumoIaUseCase.ResumoIaIdempotenciaIncompativelException.class);
    }

    private static SolicitacaoResumoIaRepositorio.Solicitacao solicitacao(
            SolicitacaoResumoIaRepositorio.Status status) {
        return new SolicitacaoResumoIaRepositorio.Solicitacao(
                SOLICITACAO, LEAD, ATENDIMENTO, status, AGORA, AGORA, null, null);
    }
}
