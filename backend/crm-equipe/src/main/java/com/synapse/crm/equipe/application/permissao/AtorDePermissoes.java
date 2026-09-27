package com.synapse.crm.equipe.application.permissao;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.domain.permissao.PoliticaDeConcessao;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Quem esta pedindo, com papel e efetivo lidos do banco — nunca do JWT. Um papel adulterado ou
 * desatualizado no token nao entra no calculo de concessao.
 */
@Component
public class AtorDePermissoes {

    private final UsuarioContext usuarios;
    private final ResolvedorDePermissoesEfetivas resolvedor;

    public AtorDePermissoes(UsuarioContext usuarios, ResolvedorDePermissoesEfetivas resolvedor) {
        this.usuarios = usuarios;
        this.resolvedor = resolvedor;
    }

    public PoliticaDeConcessao.Ator atual() {
        UsuarioAutenticado autenticado = usuarios.atual();
        ResolvedorDePermissoesEfetivas.Resolvido resolvido = resolvedor.de(autenticado.id())
                .filter(ResolvedorDePermissoesEfetivas.Resolvido::ativo)
                .filter(r -> r.papel() == autenticado.papel())
                .orElseThrow(() -> new AccessDeniedException("sessao desatualizada"));
        return new PoliticaDeConcessao.Ator(autenticado.id(), resolvido.papel(), resolvido.efetivas());
    }
}
