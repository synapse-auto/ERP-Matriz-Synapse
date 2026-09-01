package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato HTTP da disponibilidade da IA, separada da presença. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=segredo-disponibilidade-ia")
class DisponibilidadeParaIaIT extends PostgresIT {

    private static final String TOKEN = "segredo-disponibilidade-ia";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID ana;
    private UUID bruno;
    private UUID gestor;
    private UUID subgestor;

    @BeforeEach
    void preparar() {
        ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, EMAIL_ANA);
        bruno = jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, EMAIL_BRUNO);
        gestor = jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, EMAIL_GESTOR);
        subgestor = jdbc.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, EMAIL_SUBGESTOR);
        jdbc.update(
                "DELETE FROM disponibilidade_atendente_ia WHERE atendente_id IN (?, ?, ?, ?)",
                ana,
                bruno,
                gestor,
                subgestor);
        jdbc.update(
                "UPDATE usuario SET status_presenca='OFFLINE' WHERE id IN (?, ?, ?, ?)",
                ana,
                bruno,
                gestor,
                subgestor);
    }

    @Test
    void alternarDisponibilidadeNaoAlteraPresenca() {
        jdbc.update("UPDATE usuario SET status_presenca='AUSENTE' WHERE id=?", ana);

        ResponseEntity<String> resposta = patchComoGestor(ana, true);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status_presenca::text FROM usuario WHERE id=?", String.class, ana))
                .isEqualTo("AUSENTE");
        assertThat(jdbc.queryForObject(
                        "SELECT disponivel_para_ia FROM disponibilidade_atendente_ia WHERE atendente_id=?",
                        Boolean.class, ana))
                .isTrue();
    }

    @Test
    void presencaOnlineNaoLigaFlag() {
        ResponseEntity<String> resposta = chamarPresenca(ana, "ONLINE");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status_presenca::text FROM usuario WHERE id=?", String.class, ana))
                .isEqualTo("ONLINE");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM disponibilidade_atendente_ia WHERE atendente_id=?", Integer.class, ana))
                .isZero();
        assertThat(disponiveis()).doesNotContain(ana.toString());
    }

    @Test
    void atendenteOnlineComFlagDesligadaNaoAparece() {
        chamarPresenca(ana, "ONLINE");

        assertThat(disponiveis()).doesNotContain(ana.toString());
    }

    @Test
    void atendenteOfflineComFlagLigadaNaoAparece() {
        patchComoGestor(ana, true);

        assertThat(disponiveis()).doesNotContain(ana.toString());
        assertThat(jdbc.queryForObject("SELECT disponivel_para_ia FROM disponibilidade_atendente_ia WHERE atendente_id=?", Boolean.class, ana)).isTrue();
    }

    @Test
    void papelNaoAtendenteRecusadoENadaGravado() {
        ResponseEntity<String> resposta = patchComoGestor(gestor, true);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM disponibilidade_atendente_ia WHERE atendente_id=?", Integer.class, gestor)).isZero();
    }

    @Test
    void patchSubgestorPersisteENaoConfundeComGestor() {
        ResponseEntity<String> resposta = patchComoGestor(subgestor, true);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT disponivel_para_ia FROM disponibilidade_atendente_ia WHERE atendente_id=?",
                        Boolean.class,
                        subgestor))
                .isTrue();
    }

    @Test
    void subgestorOnlineComToggleLigadoEntraNaFilaDaIa() {
        jdbc.update("UPDATE usuario SET status_presenca='ONLINE' WHERE id=?", subgestor);
        patchComoGestor(subgestor, true);

        assertThat(disponiveis()).contains(subgestor.toString());
    }

    @Test
    void subgestorOnlineComToggleDesligadoNaoEntraNaFilaDaIa() {
        jdbc.update("UPDATE usuario SET status_presenca='ONLINE' WHERE id=?", subgestor);
        patchComoGestor(subgestor, false);

        assertThat(disponiveis()).doesNotContain(subgestor.toString());
    }

    @Test
    void atendenteRecebe403ENadaGravado() {
        ResponseEntity<String> resposta = patchComoAtendente(ana, true);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM disponibilidade_atendente_ia WHERE atendente_id=?", Integer.class, ana)).isZero();
    }

    private ResponseEntity<String> patchComoGestor(UUID id, boolean disponivel) {
        return chamar(EMAIL_GESTOR, SENHA_GESTOR, "/api/v1/usuarios/" + id + "/disponibilidade-ia", Map.of("disponivelParaIa", disponivel));
    }

    private ResponseEntity<String> patchComoAtendente(UUID id, boolean disponivel) {
        return chamar(EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/usuarios/" + id + "/disponibilidade-ia", Map.of("disponivelParaIa", disponivel));
    }

    private ResponseEntity<String> chamarPresenca(UUID id, String status) {
        return chamar(EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/usuarios/me/presenca", Map.of("status", status));
    }

    private ResponseEntity<String> chamar(String email, String senha, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, HttpMethod.PATCH, new HttpEntity<>(corpo, headers), String.class);
    }

    private String disponiveis() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        return http.exchange("/internal/v1/atendentes/disponiveis", HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }
}
