package com.synapse.crm.app.seguranca;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/** Login, expiracao, rotacao de refresh e RBAC. Usa os usuarios do seed de desenvolvimento. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AutenticacaoIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("login valido devolve access e refresh")
    void login_credenciaisValidas_devolveTokens() {
        var tokens = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("senha errada nao autentica")
    void login_senhaErrada_recusa() {
        var resposta = ApoioAutenticacao.tentarLogin(http, EMAIL_ANA, "senha-errada");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("usuario inativo nao autentica, mesmo com a senha certa")
    void login_usuarioInativo_recusa() {
        String email = "inativo-" + UUID.randomUUID() + "@dev.local";
        // Mesmo hash BCrypt de 'atendente123' usado no seed.
        jdbc.update(
                """
                INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
                VALUES (?, 'Desligado', ?,
                        '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42',
                        'ATENDENTE', FALSE)
                """,
                UUID.randomUUID(),
                email);

        var resposta = ApoioAutenticacao.tentarLogin(http, email, SENHA_ATENDENTE);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("token expirado e rejeitado")
    void requisicao_tokenExpirado_recusa() throws Exception {
        String expirado = jwtExpirado();

        var resposta =
                ApoioAutenticacao.comToken(http, expirado, HttpMethod.GET, "/api/v1/leads", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Rotacao: cada refresh vale uma vez. Reapresentar o antigo e indistinguivel de roubo, entao
     * derruba a familia inteira — inclusive o token novo que ja tinha sido emitido.
     */
    @Test
    @DisplayName("refresh rotativo: usar o token anterior duas vezes falha e derruba a familia")
    void refresh_tokenReutilizado_invalidaAFamilia() {
        var original = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);

        var primeira = ApoioAutenticacao.refresh(http, original.refreshToken());
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refreshNovo = (String) primeira.getBody().get("refreshToken");
        assertThat(refreshNovo).isNotEqualTo(original.refreshToken());

        // Segunda vez com o MESMO token: recusado.
        var segunda = ApoioAutenticacao.refresh(http, original.refreshToken());
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // E o sucessor tambem morre: a familia inteira foi revogada.
        var comSucessor = ApoioAutenticacao.refresh(http, refreshNovo);
        assertThat(comSucessor.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("logout invalida o refresh")
    void logout_apos_naoRenovaMais() {
        var tokens = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);

        var saida = http.postForEntity(
                "/api/v1/auth/logout", Map.of("refreshToken", tokens.refreshToken()), Void.class);
        assertThat(saida.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(ApoioAutenticacao.refresh(http, tokens.refreshToken()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("caso de uso restrito a gestao responde 403 ao atendente, nao 404 nem 500")
    void usuarios_atendente_recebe403() {
        var atendente = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);

        var resposta = ApoioAutenticacao.comToken(
                http, atendente.accessToken(), HttpMethod.GET, "/api/v1/usuarios", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("gestor executa o caso de uso restrito")
    void usuarios_gestor_recebe200() {
        var gestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR);

        var resposta = ApoioAutenticacao.comToken(
                http, gestor.accessToken(), HttpMethod.GET, "/api/v1/usuarios", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).doesNotContain("senhaHash").doesNotContain("$2a$");
    }

    @Test
    @DisplayName("sem token nao passa")
    void requisicao_semToken_recusa() {
        assertThat(http.getForEntity("/api/v1/leads", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Assina um JWT valido em tudo, menos no prazo. Mesmo segredo do contexto de teste. */
    private String jwtExpirado() throws Exception {
        Instant passado = Instant.now().minus(2, ChronoUnit.HOURS);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("papel", "ATENDENTE")
                .issueTime(java.util.Date.from(passado))
                .expirationTime(java.util.Date.from(passado.plus(15, ChronoUnit.MINUTES)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(new SecretKeySpec(SEGREDO_JWT_DE_TESTE.getBytes(), "HmacSHA256")));
        return jwt.serialize();
    }
}
