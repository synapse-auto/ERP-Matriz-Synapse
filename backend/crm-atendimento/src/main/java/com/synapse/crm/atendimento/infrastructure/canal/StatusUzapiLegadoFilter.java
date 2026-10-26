package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Mantem a URL antiga apenas para status autenticados; nunca habilita mensagens Uzapi novas. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StatusUzapiLegadoFilter extends OncePerRequestFilter {
    public static final String AUTENTICADO=StatusUzapiLegadoFilter.class.getName()+".autenticado";
    private final boolean ativo;
    private final byte[] esperado;
    private final int limite;
    public StatusUzapiLegadoFilter(CanalProperties canal,ZApiWebhookProperties entrada,
            @Value("${synapse.canal.transicao.status-uzapi-legado:false}") boolean habilitado) {
        ativo=habilitado && "z-api".equals(canal.provedor()); limite=entrada.limiteCorpoBytes();
        if(ativo && !canal.temSegredoDeWebhook()) throw new IllegalArgumentException("UZAPI_LEGADO_SEM_SEGREDO_ORIGINAL");
        esperado=hash(canal.webhookSecret()==null?"":canal.webhookSecret());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        return !ativo || !r.getRequestURI().startsWith(r.getContextPath()+"/webhook/canal");
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain) throws IOException,ServletException {
        String recebido=null;
        try {
            if(r.getQueryString()!=null) for(String parte:r.getQueryString().split("&")) {
                if(!parte.startsWith("secret=")) continue;
                if(recebido!=null) { negar(s,403); return; }
                recebido=URLDecoder.decode(parte.substring(7),StandardCharsets.UTF_8);
            }
        } catch(IllegalArgumentException erro) { negar(s,403); return; }
        if(!r.isSecure() || !"POST".equals(r.getMethod()) || !r.getRequestURI().equals(r.getContextPath()+"/webhook/canal")
                || recebido==null || !MessageDigest.isEqual(esperado,hash(recebido))) { negar(s,403); return; }
        if(r.getContentLengthLong()>limite) { negar(s,413);return; }
        if(r.getContentType()==null || !r.getContentType().split(";",2)[0].trim().equalsIgnoreCase("application/json")) {negar(s,415);return;}
        byte[] corpo=r.getInputStream().readNBytes(limite+1);
        if(corpo.length>limite) {negar(s,413);return;}
        var seguro=ZApiWebhookProtegidoFilter.semCredenciais(r,corpo,null);
        seguro.setAttribute(AUTENTICADO,Boolean.TRUE);
        chain.doFilter(seguro,s);
    }
    private static byte[] hash(String valor) {
        try {return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void negar(HttpServletResponse s,int status)throws IOException {
        s.setStatus(status);s.setContentType("application/problem+json");s.setHeader("Cache-Control","no-store");
        s.getWriter().write("{\"title\":\"Webhook recusado\",\"status\":"+status+"}");
    }
}
