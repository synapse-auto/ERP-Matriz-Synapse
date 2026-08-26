package com.synapse.crm.app.mensagemprogramada;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.core.application.mensagemprogramada.MensagemProgramadaRepositorio;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.core.domain.mensagemprogramada.StatusMensagemProgramada;

class ProcessarMensagemProgramadaUseCaseTest {

    @Test
    @DisplayName("falha ao materializar nao marca programada como ENVIADA")
    void falhaNaoMarcaComoEnviada() {
        MensagemProgramadaRepositorio mensagens = org.mockito.Mockito.mock(MensagemProgramadaRepositorio.class);
        EnviarMensagemUseCase enviar = org.mockito.Mockito.mock(EnviarMensagemUseCase.class);
        UUID id = UUID.randomUUID();
        MensagemProgramada programada = new MensagemProgramada(
                id,
                UUID.randomUUID(),
                "Lead de teste",
                UUID.randomUUID(),
                "Ana",
                "Falhar de forma observavel",
                Instant.parse("2026-08-26T15:00:00Z"),
                StatusMensagemProgramada.AGENDADA);
        when(mensagens.reservarVencida(id, Instant.parse("2026-08-26T15:00:00Z")))
                .thenReturn(Optional.of(programada));
        when(enviar.executarComoServico(
                        org.mockito.ArgumentMatchers.eq(programada.leadId()),
                        org.mockito.ArgumentMatchers.eq(programada.atendenteId()),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(id)))
                .thenThrow(new IllegalStateException("outbox indisponivel"));

        ProcessarMensagemProgramadaUseCase casoDeUso = new ProcessarMensagemProgramadaUseCase(
                mensagens,
                enviar,
                Clock.fixed(Instant.parse("2026-08-26T15:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> casoDeUso.processar(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox indisponivel");
    }
}
