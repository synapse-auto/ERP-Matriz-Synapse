package com.synapse.crm.atendimento.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class MarcarAtendimentoComoLidoUseCaseTest {
    private final AtendimentoRepositorio repositorio = mock(AtendimentoRepositorio.class);
    private final UsuarioContext usuario = mock(UsuarioContext.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final Instant agora = Instant.parse("2026-10-01T12:00:00Z");
    private final UUID leadId = UUID.randomUUID();
    private final MarcarAtendimentoComoLidoUseCase caso = new MarcarAtendimentoComoLidoUseCase(
            repositorio, usuario, Clock.fixed(agora, ZoneOffset.UTC), eventos);

    @Test
    void responsavel_publica_invalidacao_apos_registrar_propria_leitura() {
        UUID atendimentoId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        preparar(atendimentoId, responsavelId, responsavelId);
        when(repositorio.avancarVersaoDoEvento(atendimentoId)).thenReturn(1L);

        caso.executar(atendimentoId);

        var ordem = inOrder(repositorio, eventos);
        ordem.verify(repositorio).marcarComoLido(atendimentoId, responsavelId, agora);
        ordem.verify(repositorio).avancarVersaoDoEvento(atendimentoId);
        var evento = org.mockito.ArgumentCaptor.forClass(EventoCanonicoDeAtendimento.class);
        ordem.verify(eventos).publishEvent(evento.capture());
        org.assertj.core.api.Assertions.assertThat(evento.getValue().tipo())
                .isEqualTo(EventoCanonicoDeAtendimento.Tipo.LEITURA_DO_RESPONSAVEL);
        org.assertj.core.api.Assertions.assertThat(evento.getValue().leadId()).isEqualTo(leadId);
    }

    @Test
    void gestor_nao_responsavel_grava_so_sua_linha_sem_notificar_leitura_operacional() {
        UUID atendimentoId = UUID.randomUUID();
        UUID gestorId = UUID.randomUUID();
        preparar(atendimentoId, gestorId, UUID.randomUUID());

        caso.executar(atendimentoId);

        verify(repositorio).marcarComoLido(atendimentoId, gestorId, agora);
        verify(repositorio, never()).avancarVersaoDoEvento(any());
        verify(eventos, never()).publishEvent(any());
    }

    private void preparar(UUID atendimentoId, UUID usuarioId, UUID responsavelId) {
        when(usuario.atual()).thenReturn(new UsuarioAutenticado(usuarioId, PapelUsuario.GESTOR, false));
        when(repositorio.porId(atendimentoId)).thenReturn(Optional.of(new Atendimento(
                atendimentoId, leadId, null, null, responsavelId,
                StatusAtendimento.EM_ATENDIMENTO, agora, null)));
    }
}
