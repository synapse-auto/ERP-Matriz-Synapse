package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.MensagemRepositorio.StatusDeEntregaAplicado;
import com.synapse.crm.atendimento.application.referencia.MensagemIdExternoRepositorio;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusDeEntregaDoCanal;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;

class AplicarStatusDeEntregaDoCanalUseCaseTest {

    private final Instant agora = Instant.parse("2026-09-01T12:00:00Z");
    private final Clock relogio = Clock.fixed(agora, ZoneOffset.UTC);

    @Test
    void mesmoStatusDuasVezesNaoRepublicaEvento() {
        UUID mensagemId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        MensagemRepositorio mensagens = mockRepo();
        MensagemIdExternoRepositorio ids = mockIds();
        ApplicationEventPublisher eventos = org.mockito.Mockito.mock(ApplicationEventPublisher.class);

        when(mensagens.aplicarStatusDoProvedor("wamid.1", StatusEntrega.ENTREGUE, null, null))
                .thenReturn(Optional.of(new StatusDeEntregaAplicado(
                        mensagemId, atendimentoId, leadId, StatusEntrega.ENTREGUE)))
                .thenReturn(Optional.empty());
        when(ids.existe("wamid.1")).thenReturn(true);

        var useCase = new AplicarStatusDeEntregaDoCanalUseCase(mensagens, ids, eventos, relogio);
        var status = new StatusDeEntregaDoCanal("wamid.1", "ENTREGUE", null, null);
        useCase.executar(List.of(status));
        useCase.executar(List.of(status));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(MudancaDeStatusDeEntrega.class);
        MudancaDeStatusDeEntrega evento = (MudancaDeStatusDeEntrega) captor.getValue();
        assertThat(evento.mensagemId()).isEqualTo(mensagemId);
        assertThat(evento.statusEntrega()).isEqualTo("ENTREGUE");
        verify(ids).existe("wamid.1");
    }

    @Test
    void wamidDesconhecidoNaoPublicaNemEstoura() {
        MensagemRepositorio mensagens = mockRepo();
        MensagemIdExternoRepositorio ids = mockIds();
        ApplicationEventPublisher eventos = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        when(mensagens.aplicarStatusDoProvedor(eq("wamid.ghost"), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ids.existe("wamid.ghost")).thenReturn(false);

        new AplicarStatusDeEntregaDoCanalUseCase(mensagens, ids, eventos, relogio)
                .executar(List.of(new StatusDeEntregaDoCanal("wamid.ghost", "ENTREGUE", null, null)));

        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void failedPublicaFalhouComCodigo() {
        UUID mensagemId = UUID.randomUUID();
        MensagemRepositorio mensagens = mockRepo();
        MensagemIdExternoRepositorio ids = mockIds();
        ApplicationEventPublisher eventos = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        when(mensagens.aplicarStatusDoProvedor("wamid.x", StatusEntrega.FALHOU, 131053, "Media upload error"))
                .thenReturn(Optional.of(new StatusDeEntregaAplicado(
                        mensagemId, UUID.randomUUID(), UUID.randomUUID(), StatusEntrega.FALHOU)));

        new AplicarStatusDeEntregaDoCanalUseCase(mensagens, ids, eventos, relogio)
                .executar(List.of(new StatusDeEntregaDoCanal(
                        "wamid.x", "FALHOU", 131053, "Media upload error")));

        verify(mensagens)
                .aplicarStatusDoProvedor("wamid.x", StatusEntrega.FALHOU, 131053, "Media upload error");
        verify(eventos).publishEvent(any(MudancaDeStatusDeEntrega.class));
    }

    private static MensagemRepositorio mockRepo() {
        return org.mockito.Mockito.mock(MensagemRepositorio.class);
    }

    private static MensagemIdExternoRepositorio mockIds() {
        return org.mockito.Mockito.mock(MensagemIdExternoRepositorio.class);
    }
}
