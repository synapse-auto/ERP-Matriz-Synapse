package com.synapse.crm.equipe.infrastructure.seguranca;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Le o usuario da requisicao do Spring Security e o entrega tipado.
 *
 * <p>E o unico ponto do sistema que toca {@code SecurityContextHolder}. Os casos de uso recebem
 * {@link UsuarioAutenticado} e nao sabem que existe Spring Security — o teste de arquitetura reprova
 * quem tentar encurtar esse caminho.
 */
@Component
class UsuarioContextSpring implements UsuarioContext {

    @Override
    public UsuarioAutenticado atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        // Falha alto de proposito. Se "sem usuario" virasse um Optional vazio, a
        // tentacao seria tratar como "sem filtro" — que e exatamente o vazamento
        // que a RN-CRM-01 existe para impedir.
        if (autenticacao == null
                || !autenticacao.isAuthenticated()
                || !(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "Nao ha usuario autenticado na requisicao. Consulta que depende de visibilidade "
                            + "nao pode rodar fora de um contexto autenticado.");
        }

        return new UsuarioAutenticado(
                UUID.fromString(jwt.getSubject()),
                PapelUsuario.valueOf(jwt.getClaimAsString(JwtGeradorDeAccessToken.CLAIM_PAPEL)));
    }
}
