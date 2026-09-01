package com.synapse.crm.atendimento.application.painel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class ListarAtendimentosVisiveisUseCaseTest {

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
    void atendente_continuaRestritoAoProprioAtendente() {
        UUID ana = UUID.randomUUID();
        PainelDeAtendimentosRepositorio painel = mock(PainelDeAtendimentosRepositorio.class);
        UsuarioContext contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(painel.listar(VisaoAtendimento.TODOS, ana, true)).thenReturn(List.of());

        new ListarAtendimentosVisiveisUseCase(painel, contexto).executar(VisaoAtendimento.TODOS);

        verify(painel).listar(VisaoAtendimento.TODOS, ana, true);
    }
}
