package com.synapse.crm.equipe.infrastructure.seguranca;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Mantem a autoridade Spring Security alinhada ao contexto de RLS dos jobs de servico. */
@Component
public class ContextoDeServicoSpringSecurity {

    public ContextoDeServicoSpringSecurity() {
        ContextoDeServico.instalarPonteDeAutoridade(this::entrar);
    }

    private ContextoDeServico.Escopo entrar(String nomeDoServico) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        Authentication autenticacao = new UsernamePasswordAuthenticationToken(
                "servico:" + nomeDoServico,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICO")));
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        return () -> SecurityContextHolder.setContext(anterior);
    }
}
