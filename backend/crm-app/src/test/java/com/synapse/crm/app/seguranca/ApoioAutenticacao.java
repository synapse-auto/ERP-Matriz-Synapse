package com.synapse.crm.app.seguranca;

import java.util.Map;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Utilitarios de login e chamada autenticada, compartilhados pelos testes de seguranca. */
final class ApoioAutenticacao {

    /** Senhas do seed de desenvolvimento (R__seed_dev.sql). Publicas e descartaveis. */
    static final String SENHA_ATENDENTE = "atendente123";

    static final String SENHA_GESTOR = "gestor123";
    static final String EMAIL_ANA = "ana@dev.local";
    static final String EMAIL_BRUNO = "bruno@dev.local";
    static final String EMAIL_GESTOR = "gestor@dev.local";

    private ApoioAutenticacao() {}

    record Tokens(String accessToken, String refreshToken) {}

    static ResponseEntity<Map> tentarLogin(TestRestTemplate http, String email, String senha) {
        return http.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "senha", senha), Map.class);
    }

    @SuppressWarnings("unchecked")
    static Tokens login(TestRestTemplate http, String email, String senha) {
        ResponseEntity<Map> resposta = tentarLogin(http, email, senha);
        if (!resposta.getStatusCode().is2xxSuccessful()) {
            ResponseEntity<String> cru = http.postForEntity(
                    "/api/v1/auth/login", Map.of("email", email, "senha", senha), String.class);
            throw new IllegalStateException("login falhou para " + email + ": HTTP "
                    + cru.getStatusCode() + " headers=" + cru.getHeaders() + " body=" + cru.getBody());
        }
        Map<String, Object> corpo = resposta.getBody();
        return new Tokens(
                (String) corpo.get("accessToken"), (String) corpo.get("refreshToken"));
    }

    static <T> ResponseEntity<T> comToken(
            TestRestTemplate http, String token, HttpMethod metodo, String url, Class<T> tipo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(cabecalhos), tipo);
    }

    static ResponseEntity<Map> refresh(TestRestTemplate http, String refreshToken) {
        return http.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", refreshToken), Map.class);
    }
}
