package com.synapse.crm.sharedkernel.identidade;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem esta fazendo a requisicao, de forma tipada.
 *
 * <p>E o que a camada de aplicacao recebe no lugar de mexer em {@code SecurityContextHolder}: os
 * casos de uso nao precisam saber que existe Spring Security, e o teste de arquitetura cobra isso.
 */
public record UsuarioAutenticado(UUID id, PapelUsuario papel, boolean senhaProvisoria) {

    public UsuarioAutenticado {
        Objects.requireNonNull(id, "id do usuario autenticado e obrigatorio");
        Objects.requireNonNull(papel, "papel do usuario autenticado e obrigatorio");
    }

    /**
     * Conveniencia so para uso dentro deste pacote (E31b) — nenhum outro modulo pode mais
     * construir um UsuarioAutenticado sem decidir explicitamente {@code senhaProvisoria}. Existia
     * public antes, e nada no codigo de producao a usava, mas nada impedia que passasse a usar e
     * perdesse o campo por engano. Callers de teste em outros pacotes usam o construtor de 3
     * argumentos.
     */
    UsuarioAutenticado(UUID id, PapelUsuario papel) {
        this(id, papel, false);
    }

    public boolean enxergaTodosOsLeads() {
        return papel.enxergaTodosOsLeads();
    }
}
