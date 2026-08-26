package com.synapse.crm.equipe.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

class UsuarioControllerTest {

    @Test
    void respostaDaListagemExpoeCargoSemDerivarDoPapel() {
        Usuario usuario = new Usuario(
                UUID.randomUUID(), "Ana", "ana@example.invalid", "hash", PapelUsuario.ATENDENTE,
                StatusPresenca.ONLINE, true, true, null, "Consultora comercial", null,
                Instant.parse("2026-08-25T12:00:00Z"));

        UsuarioController.UsuarioResposta resposta = UsuarioController.UsuarioResposta.de(usuario);

        assertThat(resposta.cargo()).isEqualTo("Consultora comercial");
        assertThat(resposta.papel()).isEqualTo(PapelUsuario.ATENDENTE);
    }
}
