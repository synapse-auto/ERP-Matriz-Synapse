package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.timeline.OrigemEvento;

class TransferirAtendimentoUseCaseTest {

    @Test
    void transferencia_da_automacao_publica_evento_para_o_destinatario() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID atendenteId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        AtendenteParaTransferenciaRepositorio destinos = mock(AtendenteParaTransferenciaRepositorio.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento antes = Atendimento.abrirComIa(
                atendimentoId,
                leadId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-23T11:00:00Z"));
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(antes));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(antes));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        doReturn(new AtendenteParaTransferenciaRepositorio.Destino(atendenteId, "Ana"))
                .when(destinos)
                .exigirAtendenteAtivo(atendenteId);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                destinos,
                eventos,
                Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC));

        useCase.executarPelaAutomacao(atendimentoId, atendenteId);

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOf(EventoDeAtendimento.AtendimentoTransferido.class);
        EventoDeAtendimento.AtendimentoTransferido transferencia =
                (EventoDeAtendimento.AtendimentoTransferido) evento.getValue();
        assertThat(transferencia.paraAtendenteId()).isEqualTo(atendenteId);
        assertThat(transferencia.atorId()).isNull();
        assertThat(transferencia.atorTipo()).isEqualTo(OrigemEvento.AUTOMACAO);
    }
}
