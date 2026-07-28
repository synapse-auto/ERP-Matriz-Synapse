package com.synapse.crm.app.seguranca;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Simula o contexto de seguranca de uma requisicao, sem subir HTTP.
 *
 * <p>Publica desde a E04: os testes de atendimento exercitam casos de uso direto, sem controller, e
 * precisam do mesmo principal que o filtro JWT produziria.
 */
public final class ApoioRls {

    private ApoioRls() {}

    /**
     * Coloca no {@code SecurityContextHolder} um principal igual ao que o filtro JWT produziria.
     *
     * <p>E dai que {@code UsuarioContextSpring} le, e portanto e dai que o aplicador de contexto RLS
     * tira o que grava em {@code app.usuario_id} e {@code app.papel}.
     */
    public static void entrarComo(UUID usuarioId, PapelUsuario papel) {
        Jwt jwt = Jwt.withTokenValue("token-de-teste")
                .header("alg", "HS256")
                .subject(usuarioId.toString())
                .claim("papel", papel.name())
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, List.of(new SimpleGrantedAuthority("ROLE_" + papel.name()))));
    }

    /** Autenticado, mas sem ser um Jwt: o contexto RLS deve tratar como ausente. */
    static void entrarComoPrincipalDesconhecido() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("alguem", "n/a", List.of()));
    }

    public static void sair() {
        SecurityContextHolder.clearContext();
    }
}
