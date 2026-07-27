package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.equipe.domain.usuario.Usuario;

/** Adaptador JPA da porta de usuarios. */
@Repository
class UsuarioRepositorioJpa implements UsuarioRepositorio {

    private final UsuarioJpaRepository jpa;

    UsuarioRepositorioJpa(UsuarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Usuario> porEmail(String email) {
        return jpa.findByEmailIgnoreCase(email).map(UsuarioEntity::paraDominio);
    }

    @Override
    public Optional<Usuario> porId(UUID id) {
        return jpa.findById(id).map(UsuarioEntity::paraDominio);
    }

    @Override
    public List<Usuario> listarTodos() {
        return jpa.findAll().stream().map(UsuarioEntity::paraDominio).toList();
    }
}
