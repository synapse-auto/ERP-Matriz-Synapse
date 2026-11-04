package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Header customizado suportado pela tag 2.3.7; nao e assinatura do corpo. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class EvolutionWebhookProtegidoFilter extends OncePerRequestFilter {
    public static final String ROTA="/webhook/evolution",HEADER="X-Evolution-Webhook-Token";
    public static final String AUTENTICADO=EvolutionWebhookProtegidoFilter.class.getName()+".autenticado";
    private final EvolutionProperties p;
    private final ObjectMapper json;
    @org.springframework.beans.factory.annotation.Autowired
    public EvolutionWebhookProtegidoFilter(EvolutionProperties p,ObjectMapper json,CanalProperties canal,
            @org.springframework.beans.factory.annotation.Value("${synapse.seguranca.token-interno:}") String interno) {
        this(p,json);
        if(p.habilitado()&&java.util.stream.Stream.of(interno,canal.token(),canal.webhookSecret())
                .filter(v->v!=null&&!v.isBlank()).anyMatch(p.segredoWebhook()::equals))
            throw new IllegalArgumentException("EVOLUTION_WEBHOOK_SEGREDO_REUTILIZADO");
    }
    public EvolutionWebhookProtegidoFilter(EvolutionProperties p,ObjectMapper json) { this.p=p;this.json=json;if(p.habilitado())p.validar(); }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {return !r.getRequestURI().startsWith(r.getContextPath()+ROTA);}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain) throws IOException,ServletException {
        var headers=Collections.list(r.getHeaders(HEADER));
        if(!p.habilitado()||!r.getRequestURI().equals(r.getContextPath()+ROTA)||!r.isSecure()||!"POST".equals(r.getMethod())
                ||r.getQueryString()!=null||headers.size()!=1||!confere(headers.getFirst())) { recusar(s,403);return; }
        if(r.getContentLengthLong()>p.limiteCorpoBytes()) {recusar(s,413);return;}
        if(r.getContentType()==null||!r.getContentType().split(";",2)[0].trim().equalsIgnoreCase("application/json")) {recusar(s,415);return;}
        byte[] bytes=r.getInputStream().readNBytes(p.limiteCorpoBytes()+1);
        if(bytes.length>p.limiteCorpoBytes()) {recusar(s,413);return;}
        try {
            var n=json.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            if(!(n instanceof ObjectNode objeto)) {recusar(s,400);return;}
            if(!p.instancia().equals(n.path("instance").asText())) {recusar(s,403);return;}
            if(!java.util.Set.of("messages.upsert","messages.update").contains(n.path("event").asText())) {recusar(s,400);return;}
            // A Evolution inclui sua apikey no envelope. Nunca persistir/repassar essas propriedades.
            objeto.remove(java.util.List.of("apikey","destination","server_url"));
            var seguro=ZApiWebhookProtegidoFilter.semCredenciais(r,json.writeValueAsBytes(objeto),HEADER);
            seguro.setAttribute(AUTENTICADO,Boolean.TRUE);
            chain.doFilter(seguro,s);
        } catch(com.fasterxml.jackson.core.JacksonException e) {recusar(s,400);}
    }
    private boolean confere(String valor) {
        if(valor==null||valor.length()>p.limiteCorpoBytes()) return false;
        return MessageDigest.isEqual(p.segredoWebhook().getBytes(StandardCharsets.UTF_8),valor.getBytes(StandardCharsets.UTF_8));
    }
    private static void recusar(HttpServletResponse s,int status) throws IOException {
        org.slf4j.LoggerFactory.getLogger(EvolutionWebhookProtegidoFilter.class).warn("EVOLUTION_WEBHOOK_REJEITADO http_status={}",status);
        s.setStatus(status);s.setContentType("application/problem+json");s.setHeader("Cache-Control","no-store");
        s.getWriter().write("{\"title\":\"Webhook recusado\",\"status\":"+status+"}");
    }
}
