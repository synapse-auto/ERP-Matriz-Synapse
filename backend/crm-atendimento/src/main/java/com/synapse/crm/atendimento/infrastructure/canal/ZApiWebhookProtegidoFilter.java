package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Antes de observabilidade/MVC: nao le corpo sem autenticacao e nao propaga credencial. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZApiWebhookProtegidoFilter extends OncePerRequestFilter {
    public static final String ROTA = "/webhook/z-api";
    public static final String AUTENTICADO = ZApiWebhookProtegidoFilter.class.getName() + ".autenticado";
    private final ZApiWebhookProperties propriedades;
    private final AutenticacaoWebhookZApi autenticacao;

    public ZApiWebhookProtegidoFilter(ZApiWebhookProperties propriedades, ZApiProperties api,
            CanalProperties canal, @Value("${synapse.seguranca.token-interno:}") String tokenInterno) {
        this.propriedades = propriedades;
        boolean ativo = propriedades.habilitado() && api.habilitado() && "z-api".equals(canal.provedor());
        if (ativo) {
            propriedades.validar();
            // O segredo desta entrada nao pode ser nenhuma das credenciais existentes.
            if (List.of(nulo(api.token()), nulo(api.clientToken()), nulo(tokenInterno), nulo(canal.webhookSecret()))
                    .contains(propriedades.segredo())) {
                throw new IllegalArgumentException("Z_API_WEBHOOK_SEGREDO_REUTILIZADO");
            }
        }
        this.autenticacao = new AutenticacaoWebhookZApi(ativo, propriedades.segredo());
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + ROTA);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // isSecure e definido pelo TLS/container. Nunca confiar em X-Forwarded-Proto cru.
        if (!request.getRequestURI().equals(request.getContextPath() + ROTA)
                || !request.isSecure() || !"POST".equals(request.getMethod())) { recusar(response, 403); return; }
        String recebido = null;
        if (propriedades.autenticacao() == ZApiWebhookProperties.Modo.SEGREDO_URL) {
            // Nao usar getParameter: num POST form isso leria o corpo antes da autenticacao.
            recebido = segredoDaQuery(request.getQueryString());
        } else if (propriedades.autenticacao() == ZApiWebhookProperties.Modo.HEADER_NATIVO) {
            List<String> valores = Collections.list(request.getHeaders(propriedades.headerNome()));
            if (valores.size() == 1) recebido = valores.getFirst();
        }
        if (!autenticacao.confere(recebido)) { recusar(response, 403); return; }
        if (request.getContentLengthLong() > propriedades.limiteCorpoBytes()) { recusar(response, 413); return; }
        String tipo = request.getContentType();
        if (tipo == null || !tipo.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) { recusar(response, 415); return; }
        byte[] corpo = request.getInputStream().readNBytes(propriedades.limiteCorpoBytes() + 1);
        if (corpo.length > propriedades.limiteCorpoBytes()) { recusar(response, 413); return; }
        HttpServletRequest seguro = semCredenciais(request, corpo);
        seguro.setAttribute(AUTENTICADO, Boolean.TRUE);
        chain.doFilter(seguro, response);
    }

    private HttpServletRequest semCredenciais(HttpServletRequest original, byte[] corpo) {
        return new HttpServletRequestWrapper(original) {
            @Override public String getQueryString() { return null; }
            @Override public String getParameter(String nome) { return null; }
            @Override public String[] getParameterValues(String nome) { return null; }
            @Override public Map<String, String[]> getParameterMap() { return Map.of(); }
            @Override public Enumeration<String> getParameterNames() { return Collections.emptyEnumeration(); }
            private boolean sensivel(String nome) {
                return nome.equalsIgnoreCase(propriedades.headerNome()) || List.of("authorization", "client-token", "x-synapse-token")
                        .contains(nome.toLowerCase(java.util.Locale.ROOT));
            }
            @Override public String getHeader(String nome) { return sensivel(nome) ? null : super.getHeader(nome); }
            @Override public Enumeration<String> getHeaders(String nome) { return sensivel(nome) ? Collections.emptyEnumeration() : super.getHeaders(nome); }
            @Override public Enumeration<String> getHeaderNames() {
                return Collections.enumeration(Collections.list(super.getHeaderNames()).stream().filter(n -> !sensivel(n)).toList());
            }
            @Override public ServletInputStream getInputStream() {
                var entrada = new ByteArrayInputStream(corpo);
                return new ServletInputStream() {
                    @Override public int read() { return entrada.read(); }
                    @Override public boolean isFinished() { return entrada.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("WEBHOOK_SINCRONO"); }
                };
            }
            @Override public java.io.BufferedReader getReader() {
                return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        };
    }

    private static String segredoDaQuery(String query) {
        if (query == null) return null;
        String valor = null;
        for (String parte : query.split("&")) {
            // Segredo gerado em base64url: nao requer decodificacao nem aceita aliases duplicados.
            if (!parte.startsWith("secret=")) continue;
            if (valor != null) return null;
            valor = parte.substring("secret=".length());
        }
        return valor;
    }

    private static void recusar(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Webhook recusado\",\"status\":" + status + "}");
    }
    private static String nulo(String valor) { return valor == null ? "" : valor; }
}
