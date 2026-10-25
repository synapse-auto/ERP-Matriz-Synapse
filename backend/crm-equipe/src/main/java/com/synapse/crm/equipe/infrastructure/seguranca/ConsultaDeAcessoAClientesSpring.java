package com.synapse.crm.equipe.infrastructure.seguranca;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeAcessoAClientes;

@Component
class ConsultaDeAcessoAClientesSpring implements ConsultaDeAcessoAClientes {
    private final ResolvedorDePermissoesEfetivas permissoes;

    ConsultaDeAcessoAClientesSpring(ResolvedorDePermissoesEfetivas permissoes) {
        this.permissoes = permissoes;
    }

    @Override
    public boolean permitido(UUID usuarioId) {
        return permissoes.de(usuarioId).filter(ResolvedorDePermissoesEfetivas.Resolvido::ativo)
                .map(r -> r.efetivas().permite(Capacidade.CLIENTES_ACESSAR)).orElse(false);
    }
}
