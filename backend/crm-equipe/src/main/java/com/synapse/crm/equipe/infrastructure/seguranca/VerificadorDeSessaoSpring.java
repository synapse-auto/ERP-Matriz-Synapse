package com.synapse.crm.equipe.infrastructure.seguranca;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.permissao.VerificadorDeSessao;

/** Mesma decisao do {@link SessaoVigenteFilter}, para quem nao passa pela cadeia HTTP. */
@Component
class VerificadorDeSessaoSpring implements VerificadorDeSessao {

    private final ResolvedorDePermissoesEfetivas resolvedor;

    VerificadorDeSessaoSpring(ResolvedorDePermissoesEfetivas resolvedor) {
        this.resolvedor = resolvedor;
    }

    @Override
    public boolean vigente(UUID usuarioId, PapelUsuario papelDoToken) {
        return resolvedor.de(usuarioId)
                .map(r -> r.ativo() && r.papel() == papelDoToken)
                .orElse(false);
    }
}
