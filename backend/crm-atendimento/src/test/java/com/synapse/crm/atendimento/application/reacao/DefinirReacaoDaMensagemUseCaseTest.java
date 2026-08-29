package com.synapse.crm.atendimento.application.reacao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.sharedkernel.emoji.EmojiInvalidoException;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class DefinirReacaoDaMensagemUseCaseTest {

    private final AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
    private final ReacaoDeMensagemRepositorio reacoes = mock(ReacaoDeMensagemRepositorio.class);
    private final UsuarioContext usuarios = mock(UsuarioContext.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final DefinirReacaoDaMensagemUseCase caso =
            new DefinirReacaoDaMensagemUseCase(atendimentos, reacoes, usuarios, eventos);

    private final UUID atendimentoId = UUID.randomUUID();
    private final UUID mensagemId = UUID.randomUUID();
    private final Instant enviadoEm = Instant.parse("2026-08-04T10:00:01Z");
    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void autenticar() {
        when(usuarios.atual()).thenReturn(new UsuarioAutenticado(usuarioId, PapelUsuario.ATENDENTE, false));
    }

    @Test
    void payloadInvalidoNaoGrava() {
        assertThatThrownBy(() -> caso.executar(atendimentoId, mensagemId, enviadoEm, "ok"))
                .isInstanceOf(EmojiInvalidoException.class);
        verify(reacoes, never()).definir(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void atendimentoInvisivelNaoGrava() {
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.executar(atendimentoId, mensagemId, enviadoEm, "👍"))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);
        verify(reacoes, never()).definir(any(), any(), any(), any());
    }

    @Test
    void mensagemInexistenteNaoGrava() {
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(mock(Atendimento.class)));
        when(reacoes.definir(any(), eq(atendimentoId), eq(usuarioId), eq("👍"))).thenReturn(false);

        assertThatThrownBy(() -> caso.executar(atendimentoId, mensagemId, enviadoEm, "👍"))
                .isInstanceOf(RecursoDeAtendimentoIndisponivelException.class);
        verify(eventos, never()).publishEvent(any());
    }
}
