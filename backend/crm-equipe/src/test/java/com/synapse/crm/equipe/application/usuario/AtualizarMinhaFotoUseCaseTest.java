package com.synapse.crm.equipe.application.usuario;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class AtualizarMinhaFotoUseCaseTest {

    private final EquipeRepositorio equipe = Mockito.mock(EquipeRepositorio.class);
    private final UsuarioContext contexto = Mockito.mock(UsuarioContext.class);
    private final ProcessadorDeAvatar processador = Mockito.mock(ProcessadorDeAvatar.class);
    private final ArmazenamentoDeAvatar armazenamento = Mockito.mock(ArmazenamentoDeAvatar.class);
    private final LimiteDeAvatarRepositorio limite = Mockito.mock(LimiteDeAvatarRepositorio.class);
    private final UUID ana = UUID.randomUUID();
    private final Usuario atual = new Usuario(
            ana, "Ana", "ana@example.invalid", "hash", PapelUsuario.ATENDENTE,
            StatusPresenca.ONLINE, true, true, null, null, "avatar/antiga.png", null);

    @Test
    void removerLimpaReferenciaEArquivoEPermiteVoltarAsIniciais() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(equipe.porId(ana)).thenReturn(Optional.of(atual), Optional.of(atual));

        new AtualizarMinhaFotoUseCase(equipe, contexto, processador, armazenamento, limite)
                .executar(null);

        verify(equipe).atualizarFoto(ana, null);
        verify(armazenamento).remover("avatar/antiga.png");
    }

    @Test
    void falhaAoProcessarNaoApagaFotoAnterior() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(equipe.porId(ana)).thenReturn(Optional.of(atual));
        when(processador.processar(Mockito.any(byte[].class)))
                .thenThrow(new FotoDeUsuarioInvalidaException("invalida"));

        assertThatThrownBy(() -> new AtualizarMinhaFotoUseCase(
                equipe, contexto, processador, armazenamento, limite).executar(new byte[] {1}))
                .isInstanceOf(FotoDeUsuarioInvalidaException.class);

        verify(armazenamento, never()).remover("avatar/antiga.png");
        verify(equipe, never()).atualizarFoto(Mockito.any(), Mockito.any());
    }
}
