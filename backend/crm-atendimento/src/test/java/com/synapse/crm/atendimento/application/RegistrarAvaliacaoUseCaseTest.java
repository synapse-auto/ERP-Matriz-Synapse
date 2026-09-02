package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.avaliacao.AtendimentoAindaAbertoParaAvaliacaoException;
import com.synapse.crm.atendimento.domain.avaliacao.AtendimentoSemAtendenteParaAvaliacaoException;
import com.synapse.crm.atendimento.domain.avaliacao.Avaliacao;
import com.synapse.crm.atendimento.domain.avaliacao.AvaliacaoJaRegistradaException;
import com.synapse.crm.atendimento.domain.avaliacao.NotaDeAvaliacaoInvalidaException;

class RegistrarAvaliacaoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-08-28T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    void executar_atendimentoFinalizado_gravaNoAtendenteDono() {
        UUID atendimentoId = UUID.randomUUID();
        UUID atendenteId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        AvaliacaoRepositorio avaliacoes = mock(AvaliacaoRepositorio.class);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(finalizado(atendimentoId, atendenteId)));
        when(avaliacoes.porAtendimento(atendimentoId)).thenReturn(Optional.empty());
        when(avaliacoes.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        Avaliacao gravada = new RegistrarAvaliacaoUseCase(atendimentos, avaliacoes, RELOGIO)
                .executar(atendimentoId, 4, " rapido ");

        ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacoes).salvar(captor.capture());
        assertThat(captor.getValue().atendenteId()).isEqualTo(atendenteId);
        assertThat(captor.getValue().nota()).isEqualTo(4);
        assertThat(captor.getValue().comentario()).isEqualTo("rapido");
        assertThat(gravada.criadoEm()).isEqualTo(AGORA);
    }

    @Test
    void executar_atendimentoAberto_naoGrava() {
        UUID atendimentoId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        AvaliacaoRepositorio avaliacoes = mock(AvaliacaoRepositorio.class);
        Atendimento aberto = Atendimento.abrirComIa(
                        atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AGORA)
                .transferirPara(UUID.randomUUID());
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(aberto));

        assertThatThrownBy(() -> new RegistrarAvaliacaoUseCase(atendimentos, avaliacoes, RELOGIO)
                        .executar(atendimentoId, 5, null))
                .isInstanceOf(AtendimentoAindaAbertoParaAvaliacaoException.class);
        verify(avaliacoes, never()).salvar(any());
    }

    @Test
    void executar_semAtendente_naoGrava() {
        UUID atendimentoId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        AvaliacaoRepositorio avaliacoes = mock(AvaliacaoRepositorio.class);
        Atendimento soIa = Atendimento.abrirComIa(
                        atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AGORA)
                .finalizar(AGORA);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(soIa));

        assertThatThrownBy(() -> new RegistrarAvaliacaoUseCase(atendimentos, avaliacoes, RELOGIO)
                        .executar(atendimentoId, 5, null))
                .isInstanceOf(AtendimentoSemAtendenteParaAvaliacaoException.class);
        verify(avaliacoes, never()).salvar(any());
    }

    @Test
    void executar_jaAvaliado_naoGravaDeNovo() {
        UUID atendimentoId = UUID.randomUUID();
        UUID atendenteId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        AvaliacaoRepositorio avaliacoes = mock(AvaliacaoRepositorio.class);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(finalizado(atendimentoId, atendenteId)));
        when(avaliacoes.porAtendimento(atendimentoId))
                .thenReturn(Optional.of(Avaliacao.registrar(
                        UUID.randomUUID(), atendimentoId, atendenteId, 3, null, AGORA)));

        assertThatThrownBy(() -> new RegistrarAvaliacaoUseCase(atendimentos, avaliacoes, RELOGIO)
                        .executar(atendimentoId, 5, null))
                .isInstanceOf(AvaliacaoJaRegistradaException.class);
        verify(avaliacoes, never()).salvar(any());
    }

    @Test
    void executar_notaInvalida_falhaAntesDeSalvar() {
        UUID atendimentoId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        AvaliacaoRepositorio avaliacoes = mock(AvaliacaoRepositorio.class);
        when(atendimentos.porId(atendimentoId))
                .thenReturn(Optional.of(finalizado(atendimentoId, UUID.randomUUID())));
        when(avaliacoes.porAtendimento(atendimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RegistrarAvaliacaoUseCase(atendimentos, avaliacoes, RELOGIO)
                        .executar(atendimentoId, 11, null))
                .isInstanceOf(NotaDeAvaliacaoInvalidaException.class);
        verify(avaliacoes, never()).salvar(any());
    }

    @Test
    void executar_invisivel_naoConsultaAvaliacao() {
        UUID atendimentoId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        AvaliacaoRepositorio avaliacoes = mock(AvaliacaoRepositorio.class);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RegistrarAvaliacaoUseCase(atendimentos, avaliacoes, RELOGIO)
                        .executar(atendimentoId, 5, null))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);
        verify(avaliacoes, never()).porAtendimento(any());
    }

    private static Atendimento finalizado(UUID atendimentoId, UUID atendenteId) {
        return Atendimento.abrirComIa(
                        atendimentoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AGORA)
                .transferirPara(atendenteId)
                .finalizar(AGORA);
    }
}
