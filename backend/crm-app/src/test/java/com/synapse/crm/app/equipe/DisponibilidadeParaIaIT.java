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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

    @Autowired
    private ObjectMapper json;

    private UUID ana;
    private UUID bruno;
    private UUID gestor;
    private UUID subgestor;
    private UUID usuarioCriado;

    @AfterEach
    void limparUsuarioCriado() {
        if (usuarioCriado == null) {
            return;
        }
        // PostgresIT compartilha o banco entre os contextos de integração. O usuário é
        // explicitamente ativado neste cenário e precisa ser removido para não entrar no
        // rodízio nem interferir na limpeza de outras suites.
        jdbc.update("DELETE FROM audit_log WHERE ator_id = ?", usuarioCriado);
        jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id = ?", usuarioCriado);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioCriado);
        usuarioCriado = null;
    }

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

    @Test
    void usuarioCriadoPeloEndpointPermaneceForaDoRodizioAteToggleExplicito() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String email = "novo-rodizio-" + sufixo + "@teste.local";
        String senha = "Senha-Nova-42!";
        String nome = "Novo Rodizio " + sufixo;

        ResponseEntity<String> criado = chamar(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.POST,
                "/api/v1/usuarios",
                Map.of("nome", nome, "email", email, "senha", senha, "papel", "ATENDENTE"));

        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID usuarioId = UUID.fromString(json.readTree(criado.getBody()).path("id").asText());
        usuarioCriado = usuarioId;
        assertThat(json.readTree(criado.getBody()).path("disponivelParaIa").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM disponibilidade_atendente_ia WHERE atendente_id=?",
                        Integer.class,
                        usuarioId))
                .isZero();
        assertThat(usuarioNaLista(usuarioId).path("disponivelParaIa").asBoolean()).isFalse();

        String accessToken = ApoioAutenticacao.login(http, email, senha).accessToken();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM disponibilidade_atendente_ia WHERE atendente_id=?",
                        Integer.class,
                        usuarioId))
                .isZero();
        assertThat(disponiveis()).doesNotContain(usuarioId.toString());

        ResponseEntity<String> presenca = chamarComToken(
                accessToken,
                HttpMethod.PATCH,
                "/api/v1/usuarios/me/presenca",
                Map.of("status", "ONLINE"));
        // A senha definida pelo gestor e provisoria: o filtro de segurança bloqueia
        // operações do usuário até que ele a altere. O login ainda assim não cria
        // disponibilidade para IA; o caso de presença ONLINE bem-sucedida está coberto
        // pelo teste de presença com um usuário que já concluiu a troca de senha.
        assertThat(presenca.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(usuarioNaLista(usuarioId).path("disponivelParaIa").asBoolean()).isFalse();
        assertThat(disponiveis()).doesNotContain(usuarioId.toString());

        ResponseEntity<String> trocaSenha = chamarComToken(
                accessToken,
                HttpMethod.POST,
                "/api/v1/auth/senha",
                Map.of("senhaAtual", senha, "novaSenha", "Senha-Ativada-42!"));
        assertThat(trocaSenha.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenAtivado = json.readTree(trocaSenha.getBody()).path("accessToken").asText();
        ResponseEntity<String> presencaOnline = chamarComToken(
                tokenAtivado,
                HttpMethod.PATCH,
                "/api/v1/usuarios/me/presenca",
                Map.of("status", "ONLINE"));
        assertThat(presencaOnline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(usuarioNaLista(usuarioId).path("disponivelParaIa").asBoolean()).isFalse();
        assertThat(disponiveis()).doesNotContain(usuarioId.toString());

        ResponseEntity<String> subgestor = chamar(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.PUT,
                "/api/v1/usuarios/" + usuarioId,
                Map.of("nome", nome, "email", email, "papel", "SUBGESTOR"));
        assertThat(subgestor.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(subgestor.getBody()).path("disponivelParaIa").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM disponibilidade_atendente_ia WHERE atendente_id=?",
                        Integer.class,
                        usuarioId))
                .isZero();
        assertThat(disponiveis()).doesNotContain(usuarioId.toString());

        ResponseEntity<String> ativado = patchComoGestor(usuarioId, true);
        assertThat(ativado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disponiveis()).contains(usuarioId.toString());

        ResponseEntity<String> atendente = chamar(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.PUT,
                "/api/v1/usuarios/" + usuarioId,
                Map.of("nome", nome, "email", email, "papel", "ATENDENTE"));
        assertThat(atendente.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(atendente.getBody()).path("disponivelParaIa").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT disponivel_para_ia FROM disponibilidade_atendente_ia WHERE atendente_id=?",
                        Boolean.class,
                        usuarioId))
                .isTrue();
        assertThat(disponiveis()).contains(usuarioId.toString());
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
        return chamar(email, senha, HttpMethod.PATCH, url, corpo);
    }

    private ResponseEntity<String> chamar(String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return chamarComToken(token, metodo, url, corpo);
    }

    private ResponseEntity<String> chamarComToken(String token, HttpMethod metodo, String url, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, headers), String.class);
    }

    private JsonNode usuarioNaLista(UUID id) throws Exception {
        ResponseEntity<String> resposta = chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.GET, "/api/v1/usuarios", null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (JsonNode usuario : json.readTree(resposta.getBody())) {
            if (id.toString().equals(usuario.path("id").asText())) {
                return usuario;
            }
        }
        throw new AssertionError("Usuário criado não apareceu na listagem da equipe");
    }

    private String disponiveis() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        return http.exchange("/internal/v1/atendentes/disponiveis", HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }
}
