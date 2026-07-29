package com.synapse.crm.equipe.infrastructure.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica {@code /internal/v1/**} pelo cabecalho {@code X-Synapse-Token} (E07 §1) — o contrato que
 * a Automacao de todos os filhos consome. Nao e um usuario: nao ha JWT para apresentar, entao a
 * cadeia de resource server OAuth2 nunca autenticaria essa chamada.
 *
 * <p>Mesmas duas decisoes de falhar fechado de {@code MetaCloudWebhookTradutor} (E05), o unico outro
 * ponto de entrada deste sistema autenticado por segredo compartilhado em vez de JWT:
 *
 * <ul>
 *   <li>sem {@code synapse.seguranca.token-interno} configurado, <b>nada</b> passa — esquecer a
 *       variavel de ambiente num filho novo nao pode virar uma rota aberta para a internet;
 *   <li>a comparacao usa {@link MessageDigest#isEqual}, tempo independente do conteudo, para o token
 *       nao se descobrir byte a byte pelo tempo de resposta.
 * </ul>
 *
 * <p>Autentica com {@code ROLE_SERVICO} — o mesmo papel que a politica RLS ja reconhece via {@code
 * app_e_servico()} (V12) — para que casos de uso possam declarar {@code @PreAuthorize("hasRole('SERVICO')")}
 * exatamente como declaram para papeis humanos, em vez de um caminho de autorizacao paralelo so para
 * a Automacao.
 */
@Component
class SynapseTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SynapseTokenAuthenticationFilter.class);

    static final String PREFIXO_PROTEGIDO = "/internal/v1/";
    private static final String CABECALHO = "X-Synapse-Token";

    private final SegurancaProperties propriedades;

    SynapseTokenAuthenticationFilter(SegurancaProperties propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {

        if (!requisicao.getRequestURI().startsWith(PREFIXO_PROTEGIDO)) {
            cadeia.doFilter(requisicao, resposta);
            return;
        }

        if (!tokenValido(requisicao.getHeader(CABECALHO))) {
            resposta.sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-Synapse-Token ausente ou invalido");
            return;
        }

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "automacao", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICO"))));
        cadeia.doFilter(requisicao, resposta);
    }

    private boolean tokenValido(String recebido) {
        if (!propriedades.temTokenInterno()) {
            log.error("synapse.seguranca.token-interno ausente: recusando todo acesso a /internal/v1.");
            return false;
        }
        if (recebido == null) {
            return false;
        }
        byte[] esperado = propriedades.tokenInterno().getBytes(StandardCharsets.UTF_8);
        byte[] veio = recebido.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, veio);
    }
}
