package com.synapse.crm.equipe.domain.usuario;

import java.util.Objects;
import java.util.UUID;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/** Usuario do CRM. Java puro: sem JPA, sem Spring. */
public record Usuario(
        UUID id, String nome, String email, String senhaHash, PapelUsuario papel,
        StatusPresenca statusPresenca, boolean ativo) {

    public Usuario {
        Objects.requireNonNull(id, "id e obrigatorio");
        Objects.requireNonNull(email, "email e obrigatorio");
        Objects.requireNonNull(senhaHash, "senhaHash e obrigatorio");
        Objects.requireNonNull(papel, "papel e obrigatorio");
        Objects.requireNonNull(statusPresenca, "statusPresenca e obrigatorio");
    }

    public UsuarioAutenticado comoAutenticado() {
        return new UsuarioAutenticado(id, papel);
    }
}
