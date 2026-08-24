package com.synapse.crm.atendimento.application.participacao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class GerenciarParticipacaoAtendimentoUseCaseTest {
    private final UUID atendimento = UUID.randomUUID();
    private final UUID lead = UUID.randomUUID();
    private final UUID dono = UUID.randomUUID();
    private final UUID convidado = UUID.randomUUID();
    private final ParticipacaoAtendimentoRepositorio participacoes = mock(ParticipacaoAtendimentoRepositorio.class);
    private final AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
    private final UsuarioContext contexto = mock(UsuarioContext.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final UsuarioRepositorio usuarios = mock(UsuarioRepositorio.class);
    private final Clock agora = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void aprovar_recusar_entrar_e_sair_nao_escrevem_o_dono_do_atendimento() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(dono, PapelUsuario.GESTOR, false));
        when(participacoes.pedido(any())).thenReturn(Optional.of(new PedidoEntradaAtendimento(
                UUID.randomUUID(), atendimento, convidado, "Convidado", StatusPedidoEntrada.PENDENTE,
                Instant.parse("2026-08-24T11:55:00Z"))));
        when(participacoes.validadeConfigurada()).thenReturn(Duration.ofMinutes(30));
        when(participacoes.leadId(atendimento)).thenReturn(Optional.of(lead));
        when(participacoes.donoId(atendimento)).thenReturn(Optional.of(dono));
        when(participacoes.eParticipanteAtivo(atendimento, dono)).thenReturn(true);

        var useCase = new GerenciarParticipacaoAtendimentoUseCase(
                participacoes, atendimentos, contexto, eventos, agora, usuarios);
        useCase.aprovar(UUID.randomUUID());
        useCase.recusar(UUID.randomUUID());
        useCase.entrar(atendimento);
        useCase.sair(atendimento);

        verify(atendimentos, never()).salvar(any());
    }

    @Test
    void pedido_expirado_falha_no_momento_da_resposta() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(dono, PapelUsuario.GESTOR, false));
        when(participacoes.pedido(any())).thenReturn(Optional.of(new PedidoEntradaAtendimento(
                UUID.randomUUID(), atendimento, convidado, "Convidado", StatusPedidoEntrada.PENDENTE,
                Instant.parse("2026-08-24T11:00:00Z"))));
        when(participacoes.validadeConfigurada()).thenReturn(Duration.ofMinutes(30));

        var useCase = new GerenciarParticipacaoAtendimentoUseCase(
                participacoes, atendimentos, contexto, eventos, agora, usuarios);

        assertThatThrownBy(() -> useCase.aprovar(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expirado");
        verify(participacoes, never()).aprovar(any(), any(), any());
    }

    @Test
    void leitura_do_pedido_usa_relogio_injetado_para_calcular_expiracao() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(convidado, PapelUsuario.ATENDENTE, false));
        when(participacoes.validadeConfigurada()).thenReturn(Duration.ofMinutes(30));
        when(participacoes.pedidoDoSolicitante(any(), any(), any())).thenReturn(Optional.empty());

        var useCase = new GerenciarParticipacaoAtendimentoUseCase(
                participacoes, atendimentos, contexto, eventos, agora, usuarios);

        useCase.meuPedido(atendimento);

        verify(participacoes).pedidoDoSolicitante(
                atendimento, convidado, Instant.parse("2026-08-24T11:30:00Z"));
    }
}
