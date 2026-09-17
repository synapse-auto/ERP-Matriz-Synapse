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
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;

@ExtendWith(MockitoExtension.class)
class AtualizarStatusResumoIaUseCaseTest {
    private static final UUID ATENDIMENTO = UUID.randomUUID();
    private static final UUID LEAD = UUID.randomUUID();
    private static final UUID SOLICITACAO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-09-17T18:00:00Z");

    @Mock private AtendimentoRepositorio atendimentos;
    @Mock private SolicitacaoResumoIaRepositorio solicitacoes;
    @Mock private ApplicationEventPublisher eventos;

    private AtualizarStatusResumoIaUseCase caso;
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
        caso = new AtualizarStatusResumoIaUseCase(
                atendimentos, solicitacoes, eventos, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void processandoPublicaEventoSomenteDepoisDaAtualizacao() {
        when(atendimentos.porId(ATENDIMENTO)).thenReturn(Optional.of(atendimento));
        var pendente = solicitacao(SolicitacaoResumoIaRepositorio.Status.PENDENTE);
        var processando = solicitacao(SolicitacaoResumoIaRepositorio.Status.PROCESSANDO);
        when(solicitacoes.porId(SOLICITACAO)).thenReturn(Optional.of(pendente), Optional.of(processando));
        when(solicitacoes.atualizarStatus(
                        SOLICITACAO,
                        LEAD,
                        ATENDIMENTO,
                        SolicitacaoResumoIaRepositorio.Status.PROCESSANDO,
                        null,
                        null,
                        AGORA))
                .thenReturn(true);

        assertThat(caso.executar(
                        LEAD,
                        SOLICITACAO,
                        ATENDIMENTO,
                        SolicitacaoResumoIaRepositorio.Status.PROCESSANDO,
                        null,
                        null))
                .isEqualTo(processando);
        verify(eventos).publishEvent(any(Object.class));
    }

    @Test
    void cicloObsoletoNaoPermiteEscreverAposFinalizacao() {
        when(atendimentos.porId(ATENDIMENTO))
                .thenReturn(Optional.of(new Atendimento(
                        ATENDIMENTO,
                        LEAD,
                        null,
                        null,
                        atendimento.atendenteId(),
                        StatusAtendimento.FINALIZADO,
                        atendimento.iniciadoEm(),
                        AGORA)));
        when(solicitacoes.porId(SOLICITACAO)).thenReturn(Optional.of(solicitacao(
                SolicitacaoResumoIaRepositorio.Status.PROCESSANDO)));

        assertThatThrownBy(() -> caso.executar(
                        LEAD,
                        SOLICITACAO,
                        ATENDIMENTO,
                        SolicitacaoResumoIaRepositorio.Status.CONCLUIDO,
                        null,
                        null))
                .isInstanceOf(AtualizarStatusResumoIaUseCase.ResumoIaCicloObsoletoException.class);
        verify(solicitacoes, never()).atualizarStatus(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replayDoMesmoEstadoEIdempotenteMesmoSeAtendimentoPerdeuAcesso() {
        var concluido = solicitacao(SolicitacaoResumoIaRepositorio.Status.CONCLUIDO);
        when(solicitacoes.porId(SOLICITACAO)).thenReturn(Optional.of(concluido));
        assertThat(caso.executar(
                        LEAD,
                        SOLICITACAO,
                        ATENDIMENTO,
                        SolicitacaoResumoIaRepositorio.Status.CONCLUIDO,
                        null,
                        null))
                .isEqualTo(concluido);
        verify(solicitacoes, never()).atualizarStatus(any(), any(), any(), any(), any(), any(), any());
    }

    private static SolicitacaoResumoIaRepositorio.Solicitacao solicitacao(
            SolicitacaoResumoIaRepositorio.Status status) {
        return new SolicitacaoResumoIaRepositorio.Solicitacao(
                SOLICITACAO, LEAD, ATENDIMENTO, status, AGORA, AGORA, null, null);
    }
}
