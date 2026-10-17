package com.synapse.crm.equipe.infrastructure.seguranca;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.synapse.crm.equipe.application.usuario.AutorizarOperacaoDeContaUseCase;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Nenhum token contem a decisao: a propriedade e relida no banco em cada mutation. */
@Component
class ContaDeTesteFilter extends OncePerRequestFilter {
    private static final Set<String> LEITURAS_POST = Set.of(
            "/api/v1/campanhas/previa", "/api/v1/campanhas/previa/excluidos", "/api/v1/campanhas/projecao",
            "/api/v1/leads/filtrar", "/api/v1/leads/filtrar/contagem");
    private final UsuarioContext contexto;
    private final AutorizarOperacaoDeContaUseCase autorizar;
    private final ObjectMapper json;
    ContaDeTesteFilter(UsuarioContext contexto, AutorizarOperacaoDeContaUseCase autorizar, ObjectMapper json) {
        this.contexto=contexto; this.autorizar=autorizar; this.json=json;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadeia)
            throws ServletException, IOException {
        String uri=req.getRequestURI();
        boolean leitura=Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())
                || uri.startsWith("/api/v1/auth/")
                || (req.getMethod().equals("POST") && (LEITURAS_POST.contains(uri)
                    || uri.matches("/api/v1/gestao/permissoes/(usuarios|perfis)/[^/]+/copia/previa")));
        if (!uri.startsWith("/api/v1/") || leitura || contexto.atualSeHouver().isEmpty()
                || autorizar.executar(req.getMethod()+" "+uri)) {
            cadeia.doFilter(req,res); return;
        }
        ProblemDetail problema=ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Conta de teste: somente leitura na operacao normal. Nenhuma alteracao foi realizada.");
        problema.setType(URI.create("urn:synapse:conta-teste-somente-leitura"));
        problema.setTitle("Conta de teste em modo somente leitura");
        problema.setProperty("codigo","CONTA_TESTE_SOMENTE_LEITURA");
        res.setStatus(403); res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(res.getWriter(),problema);
    }
}
