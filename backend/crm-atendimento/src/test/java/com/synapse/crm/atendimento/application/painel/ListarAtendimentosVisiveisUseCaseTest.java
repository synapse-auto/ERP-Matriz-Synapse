package com.synapse.crm.atendimento.application.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class ListarAtendimentosVisiveisUseCaseTest {

    @Test
    void abasCorrespondemAoPapelSemIncluirFinalizados() {
        UUID atendente = UUID.randomUUID();
        UUID gestor = UUID.randomUUID();

        assertThat(VisaoAtendimento.abasPara(
                        new UsuarioAutenticado(atendente, PapelUsuario.ATENDENTE, false)))
                .containsExactly(
                        VisaoAtendimento.ATIVOS,
                        VisaoAtendimento.PENDENTES,
                        VisaoAtendimento.POTENCIAIS)
                .doesNotContain(VisaoAtendimento.FINALIZADOS);
        assertThat(VisaoAtendimento.abasPara(new UsuarioAutenticado(gestor, PapelUsuario.GESTOR, false)))
                .containsExactly(
                        VisaoAtendimento.ATIVOS,
                        VisaoAtendimento.PENDENTES,
                        VisaoAtendimento.POTENCIAIS,
                        VisaoAtendimento.TODOS)
                .doesNotContain(VisaoAtendimento.FINALIZADOS);
    }

    @Test
    void finalizadosESolicitavelPorAtendenteEPorGestao() {
        UUID atendente = UUID.randomUUID();
        UUID gestor = UUID.randomUUID();

        assertThat(VisaoAtendimento.solicitaveisPor(
                        new UsuarioAutenticado(atendente, PapelUsuario.ATENDENTE, false)))
                .contains(
                        VisaoAtendimento.ATIVOS,
                        VisaoAtendimento.PENDENTES,
                        VisaoAtendimento.POTENCIAIS,
                        VisaoAtendimento.FINALIZADOS)
                .doesNotContain(VisaoAtendimento.TODOS);
        assertThat(VisaoAtendimento.solicitaveisPor(
                        new UsuarioAutenticado(gestor, PapelUsuario.GESTOR, false)))
                .contains(
                        VisaoAtendimento.TODOS,
                        VisaoAtendimento.FINALIZADOS);
    }

    @Test
    void subgestor_naoFicaRestritoAoProprioAtendente() {
        UUID michele = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(michele, PapelUsuario.SUBGESTOR, false));
        when(painel.listar(VisaoAtendimento.TODOS, michele, false)).thenReturn(List.of());

        new ListarAtendimentosVisiveisUseCase(painel, contexto).executar(VisaoAtendimento.TODOS);

        verify(painel).listar(VisaoAtendimento.TODOS, michele, false);
    }

    @Test
    void atendente_naoPodePedirVisaoTodos() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));

        assertThatThrownBy(() -> new ListarAtendimentosVisiveisUseCase(painel, contexto)
                        .executar(VisaoAtendimento.TODOS))
                .isInstanceOf(AccessDeniedException.class);

        verify(painel, never()).listar(VisaoAtendimento.TODOS, ana, true);
    }

    @Test
    void atendente_naoPodePedirVisaoTodosPaginada() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));

        assertThatThrownBy(() -> new ListarAtendimentosVisiveisUseCase(painel, contexto)
                        .executarPaginado(VisaoAtendimento.TODOS, 50, false, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(painel, never()).listarPaginado(
                VisaoAtendimento.TODOS, ana, true, false, null, null, 50);
    }

    @Test
    void atendente_podePedirVisaoFinalizados() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(painel.listar(VisaoAtendimento.FINALIZADOS, ana, true)).thenReturn(List.of());

        new ListarAtendimentosVisiveisUseCase(painel, contexto).executar(VisaoAtendimento.FINALIZADOS);

        verify(painel).listar(VisaoAtendimento.FINALIZADOS, ana, true);
    }
}
