package com.synapse.crm.equipe.infrastructure.auditoria;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.sharedkernel.auditoria.LocalizadorDeEstadoAnterior;

/** Fornece o snapshot seguro do usuario para a auditoria de perfil. */
@Component
class UsuarioLocalizadorDeEstadoAnterior implements LocalizadorDeEstadoAnterior {

    private final UsuarioRepositorio usuarios;

    UsuarioLocalizadorDeEstadoAnterior(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public String entidadeTipo() {
        return "USUARIO";
    }

    @Override
    public Optional<?> carregar(UUID id) {
        return usuarios.porId(id);
    }
}
