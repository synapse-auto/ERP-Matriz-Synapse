package com.synapse.crm.app.auditoria;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * Ponta a ponta da E09a: {@code @Auditable} sobre {@code GestaoDeTagsUseCases} grava em
 * {@code audit_log} de verdade (via HTTP, o mesmo caminho que a producao usa — nao chamada direta ao
 * caso de uso, que pularia o proxy AOP), e a consulta em {@code GET /api/v1/audit-log} funciona.
 *
 * <p>A escrita e sincrona dentro do proprio request (ver o Javadoc de {@code AuditoriaAspect} sobre
 * {@code @Order}): quando o {@code TestRestTemplate} recebe a resposta, a linha de auditoria ja esta
 * commitada — nenhum destes testes precisa de espera ou retry.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AuditoriaIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("atualizar tag gera linha em audit_log com antes e depois")
    void atualizarTag_geraLinhaDeAuditoria() {
        UUID id = criarTagComoGestor("Original " + sufixo());

        var edicao = comoGestor(HttpMethod.PUT, "/api/v1/tags/" + id, corpoDeTag("Editada " + sufixo()));
        assertThat(edicao.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> linha = ultimaLinhaDeAuditoria("ATUALIZAR_TAG", id);
        assertThat(linha).isNotNull();
        assertThat(linha.get("entidade_tipo")).isEqualTo("TAG");
        assertThat((String) linha.get("dados_antes")).contains("Original");
        assertThat((String) linha.get("dados_depois")).contains("Editada");
    }

    @Test
    @DisplayName("remover tag grava o antes, sem depois")
    void removerTag_geraLinhaDeAuditoria_semDepois() {
        UUID id = criarTagComoGestor("Para remover " + sufixo());

        var remocao = comoGestor(HttpMethod.DELETE, "/api/v1/tags/" + id, null);
        assertThat(remocao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> linha = ultimaLinhaDeAuditoria("REMOVER_TAG", id);
        assertThat(linha).isNotNull();
        assertThat(linha.get("dados_antes")).isNotNull();
        assertThat(linha.get("dados_depois")).isNull();
    }

    /**
     * Nome repetido responde 409 antes de qualquer escrita (garantirNomeLivre lanca antes do
     * {@code salvar}) — o aspecto so grava depois de {@code proceed()} retornar com sucesso, entao
     * uma tentativa que nunca terminou nunca vira linha de auditoria.
     */
    @Test
    @DisplayName("tentativa que falha (nome repetido) nao gera linha de auditoria orfa")
    void criarTag_comFalha_naoAuditaTentativa() {
        String nome = "Repetida " + sufixo();
        assertThat(comoGestor(HttpMethod.POST, "/api/v1/tags", corpoDeTag(nome)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        long antes = contarAuditoria("CRIAR_TAG");

        var segunda = comoGestor(HttpMethod.POST, "/api/v1/tags", corpoDeTag(nome));
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(contarAuditoria("CRIAR_TAG")).isEqualTo(antes);
    }

    @Test
    @DisplayName("consulta com filtros combinados retorna paginada")
    void consultarAuditLog_comFiltros_retornaPaginado() {
        UUID id = criarTagComoGestor("Filtro " + sufixo());
        comoGestor(HttpMethod.PUT, "/api/v1/tags/" + id, corpoDeTag("Filtro editada " + sufixo()));

        var resposta = comoGestor(
                HttpMethod.GET,
                "/api/v1/audit-log?entidadeTipo=TAG&entidadeId=" + id + "&acao=ATUALIZAR_TAG&page=0&size=10",
                null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"totalDeItens\":1").contains("ATUALIZAR_TAG");
    }

    @Test
    @DisplayName("atendente NAO acessa o log de auditoria")
    void consultarAuditLog_atendente_recebe403() {
        var resposta = comoAtendente(HttpMethod.GET, "/api/v1/audit-log", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- apoio ---------------------------------------------------------------

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> corpoDeTag(String nome) {
        return Map.of("nome", nome, "cor", "#0EA5E9", "icone", "tag");
    }

    private UUID criarTagComoGestor(String nome) {
        var resposta = comoGestor(HttpMethod.POST, "/api/v1/tags", corpoDeTag(nome));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(resposta.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private long contarAuditoria(String acao) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE acao = ?", Long.class, acao);
        return total == null ? 0 : total;
    }

    private Map<String, Object> ultimaLinhaDeAuditoria(String acao, UUID entidadeId) {
        // dados_antes/dados_depois sao JSONB: sem o cast ::text, o driver devolve PGobject, nao
        // String — o mesmo cast que AuditLogRepositorioJdbc (producao) ja faz.
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT entidade_tipo, dados_antes::text AS dados_antes, dados_depois::text AS dados_depois "
                        + "FROM audit_log WHERE acao = ? AND entidade_id = ? ORDER BY criado_em DESC LIMIT 1",
                acao,
                entidadeId);
        return linhas.isEmpty() ? null : linhas.get(0);
    }

    private ResponseEntity<String> comoAtendente(HttpMethod metodo, String url, Object corpo) {
        return chamar(EMAIL_ANA, SENHA_ATENDENTE, metodo, url, corpo);
    }

    private ResponseEntity<String> comoGestor(HttpMethod metodo, String url, Object corpo) {
        return chamar(EMAIL_GESTOR, SENHA_GESTOR, metodo, url, corpo);
    }

    private ResponseEntity<String> chamar(String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
