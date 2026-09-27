package com.synapse.crm.sharedkernel.permissao;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Fato: o acesso efetivo de um conjunto de usuarios mudou (permissao, papel ou desativacao).
 *
 * <p>Publicado dentro da transacao que alterou o acesso e consumido com
 * {@code @TransactionalEventListener(AFTER_COMMIT)}: o cache de permissoes descarta o que calculou,
 * e o tempo real avisa as sessoes abertas para recarregar permissoes sem F5. Quando
 * {@code sessaoInvalidada} e {@code true} (papel mudou ou usuario desativado), as assinaturas de
 * WebSocket desses usuarios tambem sao descartadas — o JWT deles carrega um papel que deixou de
 * valer.
 */
public record AcessoDeUsuariosAlterado(Set<UUID> usuarios, boolean sessaoInvalidada, long revisao) {

    public AcessoDeUsuariosAlterado {
        Objects.requireNonNull(usuarios, "usuarios obrigatorio");
        usuarios = Set.copyOf(usuarios);
    }
}
