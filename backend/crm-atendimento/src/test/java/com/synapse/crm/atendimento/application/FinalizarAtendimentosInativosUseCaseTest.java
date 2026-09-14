package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;

class FinalizarAtendimentosInativosUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    void rodadaFinalizaCandidatosEContaOsQueMudaramEnquantoAguardavam() {
        var listar = mock(ListarAtendimentosInativosUseCase.class);
        var finalizar = mock(FinalizarAtendimentoUseCase.class);
        UUID finalizadoId = UUID.randomUUID();
        UUID recenteId = UUID.randomUUID();
        when(listar.executar(AGORA.minus(Duration.ofHours(24)), 50))
                .thenReturn(List.of(finalizadoId, recenteId));
        when(finalizar.executarPelaAutomacaoSeInativo(finalizadoId, AGORA.minus(Duration.ofHours(24))))
                .thenReturn(Optional.of(mock(Atendimento.class)));
        when(finalizar.executarPelaAutomacaoSeInativo(recenteId, AGORA.minus(Duration.ofHours(24))))
                .thenReturn(Optional.empty());

        var resultado = new FinalizarAtendimentosInativosUseCase(listar, finalizar)
                .executar(AGORA, Duration.ofHours(24), 50);

        assertThat(resultado.candidatos()).isEqualTo(2);
        assertThat(resultado.finalizados()).isEqualTo(1);
        assertThat(resultado.ignorados()).isEqualTo(1);
        assertThat(resultado.falhas()).isZero();
    }
}
