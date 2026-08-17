package com.synapse.crm.equipe.application.autenticacao;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.equipe.domain.usuario.Usuario;

/** Porta de acesso a usuarios. */
public interface UsuarioRepositorio {

    Optional<Usuario> porEmail(String email);

    Optional<Usuario> porId(UUID id);

    /** Troca voluntaria (E29): grava o novo hash e marca {@code senha_alterada_em = quando}. */
    void atualizarSenha(UUID usuarioId, String novoHash, Instant quando);

}
