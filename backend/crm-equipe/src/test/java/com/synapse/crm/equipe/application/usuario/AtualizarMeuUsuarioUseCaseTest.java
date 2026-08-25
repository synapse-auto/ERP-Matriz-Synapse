package com.synapse.crm.equipe.application.usuario;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.synapse.crm.equipe.application.autenticacao.CodificadorDeSenha;
import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.equipe.domain.usuario.SenhaInvalidaException;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class AtualizarMeuUsuarioUseCaseTest {

    private final EquipeRepositorio equipe = Mockito.mock(EquipeRepositorio.class);
    private final UsuarioContext contexto = Mockito.mock(UsuarioContext.class);
    private final UsuarioRepositorio usuarios = Mockito.mock(UsuarioRepositorio.class);
    private final CodificadorDeSenha senhas = Mockito.mock(CodificadorDeSenha.class);
    private final UUID ana = UUID.randomUUID();
    private final Usuario atual = new Usuario(
            ana, "Ana", "ana@example.invalid", "hash", PapelUsuario.ATENDENTE,
            StatusPresenca.ONLINE, true, true, "+55 61 99999-9999", "Consultora", null, null);

    @Test
    void sempreAtualizaSomenteOUsuarioDoContexto() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(usuarios.porId(ana)).thenReturn(Optional.of(atual));
        when(equipe.atualizarMeuPerfil(ana, "Ana nova", "ana@example.invalid", "+55 (61) 99999-9999", "Consultora"))
                .thenReturn(Optional.of(atual));

        new AtualizarMeuUsuarioUseCase(equipe, contexto, usuarios, senhas)
                .executar(ana, "Ana nova", "ana@example.invalid", "+55 (61) 99999-9999", "Consultora", null);

        verify(equipe).atualizarMeuPerfil(ana, "Ana nova", "ana@example.invalid", "+55 (61) 99999-9999", "Consultora");
        verifyNoInteractions(senhas);
    }

    @Test
    void emailOmitidoPreservaOAtualSemExigirSenha() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(usuarios.porId(ana)).thenReturn(Optional.of(atual));
        when(equipe.atualizarMeuPerfil(ana, "Ana nova", "ana@example.invalid", null, null))
                .thenReturn(Optional.of(atual));

        new AtualizarMeuUsuarioUseCase(equipe, contexto, usuarios, senhas)
                .executar(ana, "Ana nova", null, null, null, null);

        verify(equipe).atualizarMeuPerfil(ana, "Ana nova", "ana@example.invalid", null, null);
        verifyNoInteractions(senhas);
    }

    @Test
    void trocaDeEmailSemSenhaAtualEhRecusada() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(ana, PapelUsuario.ATENDENTE, false));
        when(usuarios.porId(ana)).thenReturn(Optional.of(atual));
        when(senhas.confere(null, "hash")).thenReturn(false);

        assertThatThrownBy(() -> new AtualizarMeuUsuarioUseCase(equipe, contexto, usuarios, senhas)
                .executar(ana, "Ana", "novo@example.invalid", null, null, null))
                .isInstanceOf(SenhaInvalidaException.class);
    }
}
