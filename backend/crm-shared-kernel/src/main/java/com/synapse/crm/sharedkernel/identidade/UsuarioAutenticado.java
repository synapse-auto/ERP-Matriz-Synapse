package com.synapse.crm.sharedkernel.identidade;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem esta fazendo a requisicao, de forma tipada.
 *
 * <p>E o que a camada de aplicacao recebe no lugar de mexer em {@code SecurityContextHolder}: os
 * casos de uso nao precisam saber que existe Spring Security, e o teste de arquitetura cobra isso.
 */
public record UsuarioAutenticado(UUID id, PapelUsuario papel) {

    public UsuarioAutenticado {
        Objects.requireNonNull(id, "id do usuario autenticado e obrigatorio");
        Objects.requireNonNull(papel, "papel do usuario autenticado e obrigatorio");
    }

    public boolean enxergaTodosOsLeads() {
        return papel.enxergaTodosOsLeads();
    }
}
