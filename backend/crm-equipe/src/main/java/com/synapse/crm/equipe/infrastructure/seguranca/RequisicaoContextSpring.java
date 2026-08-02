package com.synapse.crm.equipe.infrastructure.seguranca;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.synapse.crm.sharedkernel.requisicao.RequisicaoContext;

/**
 * Guarda o IP remoto da requisicao numa {@link ThreadLocal}, para o aspecto de auditoria (E09a) ler
 * de dentro do caso de uso, sem precisar de {@code HttpServletRequest} (que a camada de aplicacao nao
 * enxerga).
 *
 * <p>Le {@link HttpServletRequest#getRemoteAddr()} — nao {@code X-Forwarded-For}. Esse cabecalho e
 * definido pelo cliente e so e confiavel atras de um proxy reverso configurado para sobrescreve-lo;
 * como este projeto nao tem esse proxy configurado, confiar nele deixaria qualquer requisicao forjar
 * o IP gravado em auditoria. Se um dia houver proxy reverso confiavel na frente da aplicacao, este e
 * o unico lugar a mudar.
 */
@Component
class RequisicaoContextSpring extends OncePerRequestFilter implements RequisicaoContext {

    private static final ThreadLocal<String> IP_ATUAL = new ThreadLocal<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        IP_ATUAL.set(requisicao.getRemoteAddr());
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            IP_ATUAL.remove();
        }
    }

    @Override
    public Optional<String> ipOrigemSeHouver() {
        return Optional.ofNullable(IP_ATUAL.get());
    }
}
