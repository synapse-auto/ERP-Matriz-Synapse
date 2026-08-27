package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class FeedbacksIT extends PostgresIT {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transacao;

    @BeforeEach
    void limparFeedbacks() {
        ApoioRls.sair();
        jdbc.update("DELETE FROM feedback_usuario");
    }

    @AfterEach
    void limparContexto() {
        ApoioRls.sair();
    }

    @Test
    @DisplayName("sugestão e erro usam o autor autenticado e ignoram autor forjado")
    void criaComAutoriaDaSessao() throws Exception {
        UUID autorForjado = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_GESTOR);
        ResponseEntity<String> sugestao = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.POST,
                "/api/v1/feedbacks", Map.of(
                        "tipo", "SUGESTAO", "areaChave", "AGENDA",
                        "descricao", "  Busca por telefone  ", "autorId", autorForjado));
        ResponseEntity<String> erro = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.POST,
                "/api/v1/feedbacks", Map.of(
                        "tipo", "ERRO", "areaChave", "ATENDIMENTOS",
                        "descricao", "Falha ao abrir conversa"));

        assertThat(sugestao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(JSON.readTree(sugestao.getBody()).path("id").asText());
        UUID ana = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        assertThat(jdbc.queryForObject(
                "SELECT autor_id FROM feedback_usuario WHERE id = ?", UUID.class, id))
                .isEqualTo(ana)
                .isNotEqualTo(autorForjado);
        assertThat(jdbc.queryForObject(
                "SELECT descricao FROM feedback_usuario WHERE id = ?", String.class, id))
                .isEqualTo("Busca por telefone");
    }

    @Test
    @DisplayName("contrato rejeita tipo, área, descrição e cursor inválidos com Problem Details")
    void validacoesHttp() {
        assertThat(post(Map.of("tipo", "OUTRO", "areaChave", "GERAL", "descricao", "x"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(Map.of("tipo", "ERRO", "areaChave", "INEXISTENTE", "descricao", "x"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(Map.of("tipo", "ERRO", "areaChave", "GERAL", "descricao", "   "))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> longa = post(Map.of("tipo", "ERRO", "areaChave", "GERAL",
                "descricao", "x".repeat(2001)));
        assertThat(longa.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(longa.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        ResponseEntity<String> cursor = chamar(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR,
                HttpMethod.GET, "/api/v1/feedbacks?antesDe=2026-08-27T00:00:00Z", null);
        assertThat(cursor.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cursor.getBody()).contains("cursor");
    }

    @Test
    @DisplayName("administrador filtra e pagina sem duplicar itens")
    void administradorFiltraEPaginaSemDuplicar() throws Exception {
        post(Map.of("tipo", "SUGESTAO", "areaChave", "GERAL", "descricao", "primeira"));
        post(Map.of("tipo", "SUGESTAO", "areaChave", "EQUIPE", "descricao", "segunda"));
        post(Map.of("tipo", "ERRO", "areaChave", "AGENDA", "descricao", "terceira"));

        ResponseEntity<String> primeira = chamar(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR,
                HttpMethod.GET, "/api/v1/feedbacks?tipo=SUGESTAO&limite=1", null);
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pagina1 = JSON.readTree(primeira.getBody());
        assertThat(pagina1.path("itens")).hasSize(1);
        String primeiroId = pagina1.path("itens").get(0).path("id").asText();

        String url = "/api/v1/feedbacks?tipo=SUGESTAO&limite=1&antesDe="
                + pagina1.path("proximoCriadoEm").asText() + "&antesDoId="
                + pagina1.path("proximoId").asText();
        JsonNode pagina2 = JSON.readTree(chamar(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR,
                HttpMethod.GET, url, null).getBody());
        assertThat(pagina2.path("itens")).hasSize(1);
        assertThat(pagina2.path("itens").get(0).path("id").asText()).isNotEqualTo(primeiroId);
        assertThat(pagina2.path("itens").get(0).path("tipo").asText()).isEqualTo("SUGESTAO");
    }

    @Test
    @DisplayName("atendente, subgestor e gestor não consultam a lista global")
    void somenteAdministradorLista() {
        post(Map.of("tipo", "ERRO", "areaChave", "GERAL", "descricao", "conteúdo privado"));

        for (var acesso : List.of(
                Map.entry(EMAIL_ANA, SENHA_ATENDENTE),
                Map.entry(EMAIL_SUBGESTOR, SENHA_SUBGESTOR),
                Map.entry(EMAIL_GESTOR, SENHA_GESTOR))) {
            ResponseEntity<String> resposta = chamar(acesso.getKey(), acesso.getValue(),
                    HttpMethod.GET, "/api/v1/feedbacks", null);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(resposta.getBody()).isNullOrEmpty();
        }
    }

    @Test
    @DisplayName("requisições sem autenticação recebem 401")
    void exigeAutenticacao() {
        assertThat(http.exchange("/api/v1/feedbacks", HttpMethod.GET, HttpEntity.EMPTY, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/api/v1/feedbacks", Map.of(
                "tipo", "ERRO", "areaChave", "GERAL", "descricao", "x"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("RLS permite ao usuário ler somente o próprio feedback")
    void rlsIsolaAutores() {
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        UUID gestor = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_GESTOR);
        inserirDireto(ana, "da ana");
        inserirDireto(gestor, "do gestor");

        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        List<String> descricoes = transacao.execute(status -> jdbc.queryForList(
                "SELECT descricao FROM feedback_usuario ORDER BY descricao", String.class));

        assertThat(descricoes).containsExactly("da ana").doesNotContain("do gestor");
    }

    private ResponseEntity<String> post(Map<String, ?> corpo) {
        return chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.POST,
                "/api/v1/feedbacks", corpo);
    }

    private void inserirDireto(UUID autor, String descricao) {
        jdbc.update("""
                INSERT INTO feedback_usuario(id, autor_id, tipo, area_chave, descricao, criado_em)
                VALUES (?, ?, 'ERRO', 'GERAL', ?, ?)
                """, UUID.randomUUID(), autor, descricao,
                java.sql.Timestamp.from(Instant.now()));
    }

    private ResponseEntity<String> chamar(
            String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
