package com.synapse.crm.equipe.application.autenticacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.equipe.domain.usuario.Usuario;

/** Porta de acesso a usuarios. */
public interface UsuarioRepositorio {

    Optional<Usuario> porEmail(String email);

    Optional<Usuario> porId(UUID id);

    List<Usuario> listarTodos();
}
