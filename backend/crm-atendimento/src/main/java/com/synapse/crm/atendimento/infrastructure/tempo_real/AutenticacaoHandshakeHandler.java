package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import com.synapse.crm.sharedkernel.identidade.ClaimsJwt;

/**
 * Transforma o {@link Jwt} decodificado no handshake no {@link Principal} da sessao STOMP.
 *
 * <p>O Spring associa este {@code Principal} a TODOS os frames que chegarem por esta conexao dali em
 * diante — CONNECT, SUBSCRIBE, DISCONNECT. E o que permite {@link SecurityContextPropagationInterceptor}
 * publicar o mesmo {@code Authentication} que o REST usaria, sem o cliente reapresentar o token em
 * cada mensagem.
 *
 * <p>Usa o construtor de DOIS argumentos, {@code JwtAuthenticationToken(jwt, autoridades)} — e nao o
 * de um argumento. A diferenca nao e estilo: o construtor de um argumento chama
 * {@code setAuthenticated(false)} (o token nasce "ainda nao verificado"), e o de dois marca
 * {@code authenticated = true}. Com o de um argumento, todo {@code @PreAuthorize("isAuthenticated()")}
 * do caminho de WebSocket falharia silenciosamente — a assinatura nunca seria autorizada, e nada
 * denunciaria o motivo alem de "nao chegou mensagem nenhuma". As autoridades espelham
 * {@code SecurityConfig.conversor()} (REST), com o mesmo prefixo {@code ROLE_} e a mesma claim.
 */
@Component
class AutenticacaoHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest requisicao, WebSocketHandler manipulador, Map<String, Object> atributos) {
        Jwt jwt = (Jwt) atributos.get(JwtHandshakeInterceptor.ATRIBUTO_JWT);
        // Nunca deveria ser nulo aqui: o handshake so chega ate a construcao da
        // sessao se JwtHandshakeInterceptor.beforeHandshake devolveu true, e ele
        // so devolve true depois de gravar o Jwt neste mesmo mapa de atributos.
        List<GrantedAuthority> autoridades =
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(ClaimsJwt.PAPEL)));
        return new JwtAuthenticationToken(jwt, autoridades);
    }
}
