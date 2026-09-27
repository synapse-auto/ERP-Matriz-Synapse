package com.synapse.crm.equipe.infrastructure.seguranca;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

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

import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.sharedkernel.identidade.ClaimsJwt;

/**
 * Um JWT valido nao basta: o papel dele precisa ser o papel atual do usuario, e o usuario precisa
 * estar ativo.
 *
 * <p>Antes, desativar ou rebaixar alguem so valia no proximo refresh — ate 15 minutos (validade do
 * access token) com o poder antigo. Agora a resposta e 401 {@code sessao-desatualizada} assim que o
 * cache de permissoes enxerga a mudanca (imediato no mesmo no; no maximo
 * {@code synapse.permissoes.revalidacao} em outro). O frontend ja trata 401 renovando o token: a
 * renovacao le o papel do banco e devolve um token novo, ou recusa se o usuario foi desativado.
 *
 * <p>O custo por requisicao e uma consulta ao cache em memoria; o banco so e lido quando a revisao
 * global muda (ver {@link ResolvedorDePermissoesEfetivas}).
 */
@Component
class SessaoVigenteFilter extends OncePerRequestFilter {

    static final String TIPO = "https://synapse.crm/problemas/sessao-desatualizada";

    private final ResolvedorDePermissoesEfetivas resolvedor;
    private final ObjectMapper json;

    SessaoVigenteFilter(ResolvedorDePermissoesEfetivas resolvedor, ObjectMapper json) {
        this.resolvedor = resolvedor;
        this.json = json;
    }

    /** Sair nunca pode ficar preso: logout com token desatualizado continua funcionando. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        return "POST".equalsIgnoreCase(requisicao.getMethod()) && "/api/v1/auth/logout".equals(requisicao.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.isAuthenticated() && autenticacao.getPrincipal() instanceof Jwt jwt
                && !vigente(jwt)) {
            escreverProblema(resposta);
            return;
        }
        cadeia.doFilter(requisicao, resposta);
    }

    private boolean vigente(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
        String papelDoToken = jwt.getClaimAsString(ClaimsJwt.PAPEL);
        Optional<ResolvedorDePermissoesEfetivas.Resolvido> atual = resolvedor.de(id);
        return atual.isPresent() && atual.get().ativo() && atual.get().papel().name().equals(papelDoToken);
    }

    private void escreverProblema(HttpServletResponse resposta) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Seu acesso mudou. Renove a sessao para continuar.");
        problema.setTitle("Sessao desatualizada");
        problema.setType(java.net.URI.create(TIPO));
        resposta.setStatus(HttpStatus.UNAUTHORIZED.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.getWriter().write(json.writeValueAsString(problema));
    }
}
