package com.synapse.crm.equipe.infrastructure.seguranca;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.synapse.crm.sharedkernel.identidade.ClaimsJwt;

/**
 * Bloqueia toda rota autenticada enquanto a senha do usuario for provisoria (E29).
 *
 * <p>O bloqueio e no servidor, nao na tela: o access token continua criptograficamente valido, e um
 * frontend que so redirecionasse deixaria a API respondendo normalmente para qualquer chamada
 * direta. Duas rotas escapam de proposito: trocar a propria senha (unico jeito de sair do estado) e
 * logout (para nunca prender ninguem numa sessao).
 *
 * <p>Precisa rodar DEPOIS que o resource server OAuth2 autentica o JWT — por isso a
 * {@link SecurityConfig} o registra com {@code addFilterAfter(..., BearerTokenAuthenticationFilter.class)},
 * nunca antes: so ali o {@link Jwt} ja esta no {@code SecurityContextHolder} para este filtro ler a
 * claim {@link ClaimsJwt#SENHA_PROVISORIA}.
 */
@Component
class SenhaProvisoriaFilter extends OncePerRequestFilter {

    private static final String ROTA_TROCAR_SENHA = "/api/v1/auth/senha";
    private static final String ROTA_LOGOUT = "/api/v1/auth/logout";

    private final ObjectMapper json;

    SenhaProvisoriaFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        boolean provisoria = autenticacao != null
                && autenticacao.isAuthenticated()
                && autenticacao.getPrincipal() instanceof Jwt jwt
                && Boolean.TRUE.equals(jwt.getClaimAsBoolean(ClaimsJwt.SENHA_PROVISORIA));

        if (provisoria && !rotaLivre(requisicao)) {
            escreverProblema(resposta);
            return;
        }
        cadeia.doFilter(requisicao, resposta);
    }

    private boolean rotaLivre(HttpServletRequest requisicao) {
        if (!"POST".equalsIgnoreCase(requisicao.getMethod())) {
            return false;
        }
        String uri = requisicao.getRequestURI();
        return uri.equals(ROTA_TROCAR_SENHA) || uri.equals(ROTA_LOGOUT);
    }

    private void escreverProblema(HttpServletResponse resposta) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Senha provisoria: troque a senha antes de continuar.");
        problema.setTitle("Senha provisoria");
        problema.setProperty("acao", ROTA_TROCAR_SENHA);

        resposta.setStatus(HttpStatus.FORBIDDEN.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.getWriter().write(json.writeValueAsString(problema));
    }
}
