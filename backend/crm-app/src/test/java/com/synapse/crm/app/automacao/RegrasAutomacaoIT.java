package com.synapse.crm.app.automacao;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
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

/** Testes HTTP do CRUD administrativo das regras da automação (E35b, Bloco 1). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=segredo-de-teste-do-internal-v1")
class RegrasAutomacaoIT extends PostgresIT {

    private static final String PREFIXO = "e35b-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void limparDadosDeTeste() {
        jdbc.update("DELETE FROM regra_follow_up WHERE texto LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM regra_fidelizacao WHERE mensagem LIKE ?", PREFIXO + "%");
    }

    @Test
    void followUp_criaListaAtualizaAlternaEExcluiPelaRota() {
        String texto = PREFIXO + "crud-follow-" + UUID.randomUUID();

        ResponseEntity<String> criado = comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 1440, "texto", texto, "ativo", true));
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode criadoJson = json(criado);
        UUID id = UUID.fromString(criadoJson.get("id").asText());
        assertThat(criadoJson.get("nome").asText()).isEqualTo("1 dia sem resposta");
        assertThat(criadoJson.get("tempoMinutos").asInt()).isEqualTo(1440);

        ResponseEntity<String> listado = comoGestor(HttpMethod.GET, "/api/v1/automacao/follow-ups", null);
        assertThat(listado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listado.getBody()).contains(texto);

        ResponseEntity<String> atualizado = comoGestor(HttpMethod.PUT, "/api/v1/automacao/follow-ups/" + id,
                Map.of("tempoMinutos", 90, "texto", texto + "-atualizado", "ativo", true));
        assertThat(atualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(atualizado).get("nome").asText()).isEqualTo("1,5 horas sem resposta");

        ResponseEntity<String> alternado = comoGestor(HttpMethod.PATCH,
                "/api/v1/automacao/follow-ups/" + id + "/ativo", Map.of("ativo", false));
        assertThat(alternado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(alternado).get("ativo").asBoolean()).isFalse();

        ResponseEntity<String> excluido = comoGestor(HttpMethod.DELETE, "/api/v1/automacao/follow-ups/" + id, null);
        assertThat(excluido.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(contarFollowUps(texto + "-atualizado")).isZero();
    }

    @Test
    void fidelizacao_criaListaAtualizaAlternaEExcluiPelaRota() {
        String mensagem = PREFIXO + "crud-fidelizacao-" + UUID.randomUUID();

        ResponseEntity<String> criado = comoGestor(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                Map.of("diasSemContato", 30, "mensagem", mensagem, "ativo", true));
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(json(criado).get("id").asText());

        assertThat(comoGestor(HttpMethod.GET, "/api/v1/automacao/fidelizacao", null).getBody()).contains(mensagem);

        ResponseEntity<String> atualizado = comoGestor(HttpMethod.PUT, "/api/v1/automacao/fidelizacao/" + id,
                Map.of("diasSemContato", 45, "mensagem", mensagem + "-atualizada", "ativo", true));
        assertThat(atualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(atualizado).get("diasSemContato").asInt()).isEqualTo(45);

        ResponseEntity<String> alternado = comoGestor(HttpMethod.PATCH,
                "/api/v1/automacao/fidelizacao/" + id + "/ativo", Map.of("ativo", false));
        assertThat(alternado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(alternado).get("ativo").asBoolean()).isFalse();

        ResponseEntity<String> excluido = comoGestor(HttpMethod.DELETE, "/api/v1/automacao/fidelizacao/" + id, null);
        assertThat(excluido.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(contarFidelizacoes(mensagem + "-atualizada")).isZero();
    }

    @Test
    void atendenteRecebe403ENadaGrava() {
        String texto = PREFIXO + "sem-permissao-" + UUID.randomUUID();
        long antesFollow = contarFollowUps(texto);
        long antesFidelizacao = contarFidelizacoes(texto);

        ResponseEntity<String> follow = comoAtendente(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 60, "texto", texto, "ativo", true));
        ResponseEntity<String> fidelizacao = comoAtendente(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                Map.of("diasSemContato", 10, "mensagem", texto, "ativo", true));

        assertThat(follow.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(fidelizacao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(contarFollowUps(texto)).isEqualTo(antesFollow);
        assertThat(contarFidelizacoes(texto)).isEqualTo(antesFidelizacao);
    }

    @Test
    void listagensOrdenamFollowUpEFidelizacaoPeloValor() {
        String followMaior = PREFIXO + "ordem-follow-maior-" + UUID.randomUUID();
        String followMenor = PREFIXO + "ordem-follow-menor-" + UUID.randomUUID();
        String fidelizacaoMaior = PREFIXO + "ordem-fidelizacao-maior-" + UUID.randomUUID();
        String fidelizacaoMenor = PREFIXO + "ordem-fidelizacao-menor-" + UUID.randomUUID();

        comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 1440, "texto", followMaior, "ativo", true));
        comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 60, "texto", followMenor, "ativo", true));
        comoGestor(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                Map.of("diasSemContato", 30, "mensagem", fidelizacaoMaior, "ativo", true));
        comoGestor(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                Map.of("diasSemContato", 5, "mensagem", fidelizacaoMenor, "ativo", true));

        String followBody = comoGestor(HttpMethod.GET, "/api/v1/automacao/follow-ups", null).getBody();
        String fidelizacaoBody = comoGestor(HttpMethod.GET, "/api/v1/automacao/fidelizacao", null).getBody();
        assertThat(followBody.indexOf(followMenor)).isLessThan(followBody.indexOf(followMaior));
        assertThat(fidelizacaoBody.indexOf(fidelizacaoMenor)).isLessThan(fidelizacaoBody.indexOf(fidelizacaoMaior));
    }

    @Test
    void placeholderTelefone_devolve422NomeiaRecusadoEVálidoENaoGrava() {
        String texto = PREFIXO + "placeholder-telefone-" + UUID.randomUUID();
        ResponseEntity<String> resposta = comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 60, "texto", texto + " {telefone}", "ativo", true));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("{telefone}").contains("{nome}");
        assertThat(contarFollowUps(texto + " {telefone}")).isZero();

        ResponseEntity<String> aceito = comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 60, "texto", texto + " {nome}", "ativo", true));
        assertThat(aceito.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(contarFollowUps(texto + " {nome}")).isEqualTo(1);
    }

    @Test
    void mensagensVaziaOuComEspacos_devolve422ENaoGrava() {
        for (String mensagem : new String[] {"", "   "}) {
            ResponseEntity<String> follow = comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                    Map.of("tempoMinutos", 60, "texto", mensagem, "ativo", true));
            ResponseEntity<String> fidelizacao = comoGestor(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                    Map.of("diasSemContato", 10, "mensagem", mensagem, "ativo", true));
            assertThat(follow.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(fidelizacao.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(contarFollowUps(mensagem)).isZero();
            assertThat(contarFidelizacoes(mensagem)).isZero();
        }
    }

    @Test
    void tempoZeroOuNegativo_devolve422DaAplicacaoENaoGrava() {
        for (int tempo : new int[] {0, -1}) {
            String texto = PREFIXO + "tempo-invalido-" + tempo;
            ResponseEntity<String> resposta = comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                    Map.of("tempoMinutos", tempo, "texto", texto, "ativo", true));
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("maior que zero").doesNotContain("constraint");
            assertThat(contarFollowUps(texto)).isZero();
        }
    }

    @Test
    void diasSemContatoZeroOuNegativos_devolve422ENaoGrava() {
        for (int dias : new int[] {0, -1}) {
            String mensagem = PREFIXO + "dias-invalidos-" + dias;
            ResponseEntity<String> resposta = comoGestor(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                    Map.of("diasSemContato", dias, "mensagem", mensagem, "ativo", true));
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody()).contains("maiores que zero");
            assertThat(contarFidelizacoes(mensagem)).isZero();
        }
    }

    @Test
    void atualizacaoInvalida_devolve422EConservaValorAnterior() {
        String texto = PREFIXO + "update-invalido-follow";
        ResponseEntity<String> criado = comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 60, "texto", texto, "ativo", true));
        UUID followId = UUID.fromString(json(criado).get("id").asText());
        ResponseEntity<String> followInvalido = comoGestor(HttpMethod.PUT, "/api/v1/automacao/follow-ups/" + followId,
                Map.of("tempoMinutos", 0, "texto", texto + " {telefone}", "ativo", true));
        assertThat(followInvalido.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(comoGestor(HttpMethod.GET, "/api/v1/automacao/follow-ups", null).getBody())
                .contains(texto).contains("\"tempoMinutos\":60");

        String mensagem = PREFIXO + "update-invalido-fidelizacao";
        ResponseEntity<String> fidelizacao = comoGestor(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                Map.of("diasSemContato", 10, "mensagem", mensagem, "ativo", true));
        UUID fidelizacaoId = UUID.fromString(json(fidelizacao).get("id").asText());
        ResponseEntity<String> fidelizacaoInvalida = comoGestor(HttpMethod.PUT,
                "/api/v1/automacao/fidelizacao/" + fidelizacaoId,
                Map.of("diasSemContato", -1, "mensagem", mensagem + " {telefone}", "ativo", true));
        assertThat(fidelizacaoInvalida.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(comoGestor(HttpMethod.GET, "/api/v1/automacao/fidelizacao", null).getBody())
                .contains(mensagem).contains("\"diasSemContato\":10");
    }

    @Test
    void regraAtivaApareceNoContratoInternoInativaSomeEMantemListaAdministrativa() {
        String followTexto = PREFIXO + "interno-follow";
        String fidelizacaoMensagem = PREFIXO + "interno-fidelizacao";
        UUID followId = UUID.fromString(json(comoGestor(HttpMethod.POST, "/api/v1/automacao/follow-ups",
                Map.of("tempoMinutos", 60, "texto", followTexto, "ativo", true))).get("id").asText());
        UUID fidelizacaoId = UUID.fromString(json(comoGestor(HttpMethod.POST, "/api/v1/automacao/fidelizacao",
                Map.of("diasSemContato", 10, "mensagem", fidelizacaoMensagem, "ativo", true))).get("id").asText());

        assertThat(comTokenInterno("/internal/v1/regras/follow-up").getBody()).contains(followTexto);
        assertThat(comTokenInterno("/internal/v1/regras/fidelizacao").getBody()).contains(fidelizacaoMensagem);

        assertThat(comoGestor(HttpMethod.PATCH, "/api/v1/automacao/follow-ups/" + followId + "/ativo",
                Map.of("ativo", false)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(comoGestor(HttpMethod.PATCH, "/api/v1/automacao/fidelizacao/" + fidelizacaoId + "/ativo",
                Map.of("ativo", false)).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(comTokenInterno("/internal/v1/regras/follow-up").getBody()).doesNotContain(followTexto);
        assertThat(comTokenInterno("/internal/v1/regras/fidelizacao").getBody()).doesNotContain(fidelizacaoMensagem);
        assertThat(comoGestor(HttpMethod.GET, "/api/v1/automacao/follow-ups", null).getBody())
                .contains(followTexto).contains("\"ativo\":false");
        assertThat(comoGestor(HttpMethod.GET, "/api/v1/automacao/fidelizacao", null).getBody())
                .contains(fidelizacaoMensagem).contains("\"ativo\":false");
    }

    @Test
    void rotasInternasSemOuComTokenInvalido_devolvem401() {
        for (String rota : new String[] {"/internal/v1/regras/follow-up", "/internal/v1/regras/fidelizacao"}) {
            ResponseEntity<String> semToken = http.exchange(rota, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            ResponseEntity<String> tokenInvalido = comTokenInterno(rota, "token-invalido");
            assertThat(semToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(tokenInvalido.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<String> comoGestor(HttpMethod metodo, String rota, Map<String, ?> corpo) {
        return chamar(metodo, rota, corpo, ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken());
    }

    private ResponseEntity<String> comoAtendente(HttpMethod metodo, String rota, Map<String, ?> corpo) {
        return chamar(metodo, rota, corpo, ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken());
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String rota, Map<String, ?> corpo, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(rota, metodo, new HttpEntity<>(corpo, headers), String.class);
    }

    private ResponseEntity<String> comTokenInterno(String rota) {
        return comTokenInterno(rota, "segredo-de-teste-do-internal-v1");
    }

    private ResponseEntity<String> comTokenInterno(String rota, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", token);
        return http.exchange(rota, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> resposta) {
        try {
            return objectMapper.readTree(resposta.getBody());
        } catch (Exception e) {
            throw new AssertionError("resposta nao e JSON: " + resposta, e);
        }
    }

    private long contarFollowUps(String texto) {
        return jdbc.queryForObject("SELECT count(*) FROM regra_follow_up WHERE texto = ?", Long.class, texto);
    }

    private long contarFidelizacoes(String mensagem) {
        return jdbc.queryForObject("SELECT count(*) FROM regra_fidelizacao WHERE mensagem = ?", Long.class, mensagem);
    }
}
