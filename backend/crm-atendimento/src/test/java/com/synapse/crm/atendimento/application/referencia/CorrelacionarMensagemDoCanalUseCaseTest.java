package com.synapse.crm.atendimento.application.referencia;

import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.MensagemRepositorio.StatusDeEntregaAplicado;
import com.synapse.crm.atendimento.application.referencia.CorrelacionarMensagemDoCanalUseCase.Vinculo;
import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusComIdentidade;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;

class CorrelacionarMensagemDoCanalUseCaseTest {
    private final CorrelacaoDeMensagemDoCanalRepositorio repositorio = mock(CorrelacaoDeMensagemDoCanalRepositorio.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final Instant agora = Instant.parse("2026-10-24T12:00:00Z");
    private final IdentidadeDeMensagemDoCanal chave = new IdentidadeDeMensagemDoCanal("z-api", "A", "WHATSAPP", "w-1");
    private final UUID mensagem = UUID.randomUUID(), atendimento = UUID.randomUUID(), lead = UUID.randomUUID();
    private final CorrelacionarMensagemDoCanalUseCase caso = new CorrelacionarMensagemDoCanalUseCase(repositorio, eventos, Clock.fixed(agora, ZoneOffset.UTC));

    @Test void vinculoAceitoAplicaStatusAntecipadoEPublicaMudanca() {
        when(repositorio.registrar(chave, mensagem, agora, atendimento)).thenReturn(true);
        when(repositorio.aplicar(chave)).thenReturn(Optional.of(new StatusDeEntregaAplicado(mensagem, atendimento, lead, StatusEntrega.LIDO)));
        caso.executar(List.of(new Vinculo(chave, mensagem, agora, atendimento)), List.of());
        verify(eventos).publishEvent(new MudancaDeStatusDeEntrega(mensagem, atendimento, lead, "LIDO", agora));
    }

    @Test void conflitoNaoRoubaAliasNemAplicaStatusDaOutraMensagem() {
        when(repositorio.registrar(chave, mensagem, agora, atendimento)).thenReturn(false);
        caso.executar(List.of(new Vinculo(chave, mensagem, agora, atendimento)), List.of());
        verify(repositorio, never()).aplicar(any());
        verifyNoInteractions(eventos);
    }

    @Test void observacaoDuplicadaSemMudancaNaoPublicaEvento() {
        var observacao = new StatusComIdentidade(chave, StatusEntrega.LIDO, agora);
        when(repositorio.aplicar(chave)).thenReturn(Optional.empty());
        caso.executar(List.of(), List.of(observacao));
        var ordem = inOrder(repositorio);
        ordem.verify(repositorio).observar(observacao);
        ordem.verify(repositorio).aplicar(chave);
        verifyNoInteractions(eventos);
    }
}
