package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * Tags e etapas: leitura para todos, escrita so para gestao (RN-CRM-03).
 *
 * <p>Tags sao da operacao inteira. Se um atendente pudesse cria-las, em duas semanas existiriam
 * quinze variacoes de "orcamento" e relatorio por tag deixaria de significar alguma coisa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class TagsEEtapasIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("atendente NAO cria tag")
    void criarTag_atendente_recebe403() {
        var resposta = comoAtendente(HttpMethod.POST, "/api/v1/tags", corpoDeTag("Nova " + sufixo()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("atendente NAO edita tag")
    void editarTag_atendente_recebe403() {
        UUID id = criarTagComoGestor();

        var resposta =
                comoAtendente(HttpMethod.PUT, "/api/v1/tags/" + id, corpoDeTag("Editada " + sufixo()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("atendente NAO remove tag")
    void removerTag_atendente_recebe403() {
        UUID id = criarTagComoGestor();

        var resposta = comoAtendente(HttpMethod.DELETE, "/api/v1/tags/" + id, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("atendente le as tags normalmente")
    void listarTags_atendente_recebe200() {
        var resposta = comoAtendente(HttpMethod.GET, "/api/v1/tags", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("gestao cria, edita e remove tag")
    void cicloDeVidaDaTag_gestor_funciona() {
        UUID id = criarTagComoGestor();

        var edicao = comoGestor(HttpMethod.PUT, "/api/v1/tags/" + id, corpoDeTag("Editada " + sufixo()));
        assertThat(edicao.getStatusCode()).isEqualTo(HttpStatus.OK);

        var remocao = comoGestor(HttpMethod.DELETE, "/api/v1/tags/" + id, null);
        assertThat(remocao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("nome de tag repetido responde 409, nao 500")
    void criarTag_nomeRepetido_recebe409() {
        String nome = "Repetida " + sufixo();
        assertThat(comoGestor(HttpMethod.POST, "/api/v1/tags", corpoDeTag(nome)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        var segunda = comoGestor(HttpMethod.POST, "/api/v1/tags", corpoDeTag(nome));

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("atendente le as etapas, mas nao as cria")
    void etapas_atendente_leMasNaoEscreve() {
        assertThat(comoAtendente(HttpMethod.GET, "/api/v1/etapas", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> nova = Map.of("nome", "Etapa " + sufixo(), "ordem", 90, "corVisual", "#fff");
        assertThat(comoAtendente(HttpMethod.POST, "/api/v1/etapas", nova).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("etapas vem do banco, na ordem do funil")
    void etapas_seedDeDesenvolvimento_vemEmOrdem() {
        String corpo = comoAtendente(HttpMethod.GET, "/api/v1/etapas", null).getBody();

        assertThat(corpo).contains("Novo contato").contains("\"resultado\":\"GANHO\"");
        assertThat(corpo.indexOf("Novo contato")).isLessThan(corpo.indexOf("Pos-venda"));
    }

    @Test
    @DisplayName("gestor configura o resultado comercial da etapa")
    void etapa_gestorConfiguraResultado() {
        Map<String, Object> nova = Map.of(
                "nome", "Descartada " + sufixo(),
                "ordem", 91,
                "corVisual", "#fff",
                "resultado", "PERDIDO");

        var resposta = comoGestor(HttpMethod.POST, "/api/v1/etapas", nova);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).contains("\"resultado\":\"PERDIDO\"");
    }

    // --- apoio ---------------------------------------------------------------

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> corpoDeTag(String nome) {
        return Map.of("nome", nome, "cor", "#0EA5E9", "icone", "tag");
    }

    private UUID criarTagComoGestor() {
        var resposta = comoGestor(HttpMethod.POST, "/api/v1/tags", corpoDeTag("Tag " + sufixo()));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(resposta.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private ResponseEntity<String> comoAtendente(HttpMethod metodo, String url, Object corpo) {
        return chamar(EMAIL_ANA, SENHA_ATENDENTE, metodo, url, corpo);
    }

    private ResponseEntity<String> comoGestor(HttpMethod metodo, String url, Object corpo) {
        return chamar(EMAIL_GESTOR, SENHA_GESTOR, metodo, url, corpo);
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
