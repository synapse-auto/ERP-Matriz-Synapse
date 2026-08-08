package com.synapse.crm.app.automacao;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * O contrato {@code /internal/v1} e a configuracao da automacao (E07 §6).
 *
 * <p>Redis real via Testcontainers (nao o fallback de "sem cache"): sem isso, o teste de "reflete sem
 * redeploy" passaria mesmo se a invalidacao do cache estivesse quebrada — a leitura simplesmente cairia
 * direto no banco, e o bug que o teste existe para pegar ficaria invisivel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=segredo-de-teste-do-internal-v1")
class ContratoAutomacaoIT extends PostgresIT {

    private static final String TOKEN_VALIDO = "segredo-de-teste-do-internal-v1";
    private static final String CHAVE_TESTE = "e07-teste-numerico";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepararParametroDeTeste() {
        jdbc.update("DELETE FROM configuracao_automacao WHERE chave = ?", CHAVE_TESTE);
        jdbc.update(
                """
                INSERT INTO configuracao_automacao (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
                VALUES (?, '10', 'minutos', 'INT', 1, 100, 'parametro de teste da E07')
                """,
                CHAVE_TESTE);
    }

    @Nested
    @DisplayName("autenticacao de /internal/v1")
    class Autenticacao {

        @Test
        @DisplayName("sem token, /internal/v1 devolve 401")
        void semToken_devolve401() {
            ResponseEntity<String> resposta =
                    http.exchange("/internal/v1/automation-config", HttpMethod.GET, semCabecalhos(), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("com token invalido, /internal/v1 devolve 401")
        void tokenInvalido_devolve401() {
            ResponseEntity<String> resposta = comToken("token-forjado", "/internal/v1/automation-config");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        /**
         * O ponto central do E07 §1: nenhum usuario autenticou, so o token estatico — e mesmo assim a
         * leitura funciona. E a prova de que "contexto de servico" nao e so um comentario no codigo.
         */
        @Test
        @DisplayName("com token valido, a Automacao le configuracao sem usuario autenticado")
        void tokenValido_leSemUsuarioAutenticado() {
            ResponseEntity<String> resposta = comToken(TOKEN_VALIDO, "/internal/v1/automation-config");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).contains(CHAVE_TESTE);
        }
    }

    @Nested
    @DisplayName("configuracao de automacao")
    class Configuracao {

        @Test
        @DisplayName("alterar um parametro reflete em /internal/v1 sem redeploy")
        void alterarParametro_refleteSemRedeploy() {
            // Primeira leitura povoa o cache com o valor antigo.
            ResponseEntity<String> antes = comToken(TOKEN_VALIDO, "/internal/v1/automation-config/" + CHAVE_TESTE);
            assertThat(antes.getBody()).contains("\"valor\":\"10\"");

            atualizarComoGestor(CHAVE_TESTE, "42");

            ResponseEntity<String> depois = comToken(TOKEN_VALIDO, "/internal/v1/automation-config/" + CHAVE_TESTE);
            assertThat(depois.getBody()).contains("\"valor\":\"42\"");
        }

        @Test
        @DisplayName("valor fora da faixa e rejeitado com mensagem clara")
        void valorForaDaFaixa_rejeitadoComMensagemClara() {
            ResponseEntity<String> resposta = putComoGestor(CHAVE_TESTE, "999");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody()).contains("nao pode ser maior que");
        }

        private void atualizarComoGestor(String chave, String novoValor) {
            ResponseEntity<String> resposta = putComoGestor(chave, novoValor);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        private ResponseEntity<String> putComoGestor(String chave, String novoValor) {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken());
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
            return http.exchange(
                    "/api/v1/automacao/config/" + chave,
                    HttpMethod.PUT,
                    new HttpEntity<>(java.util.Map.of("valor", novoValor), cabecalhos),
                    String.class);
        }
    }

    @Nested
    @DisplayName("painel administrativo (GET /api/v1/automacao/config)")
    class PainelAdmin {

        @Test
        @DisplayName("sem token, devolve 401")
        void semToken_devolve401() {
            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/automacao/config", HttpMethod.GET, semCabecalhos(), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("atendente nao pode ler — 403, o mesmo papel que ja nao pode editar")
        void atendente_devolve403() {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken());

            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/automacao/config", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("gestor le valor, faixa e unidade do parametro de teste")
        void gestor_leValorFaixaEUnidade() {
            ResponseEntity<String> resposta = listarComoGestor();

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody())
                    .contains("\"chave\":\"" + CHAVE_TESTE + "\"")
                    .contains("\"valor\":\"10\"")
                    .contains("\"unidade\":\"minutos\"")
                    .contains("\"valorMin\":1")
                    .contains("\"valorMax\":100");
        }

        @Test
        @DisplayName("subgestor tambem le — mesma autorizacao do PUT, nao so o gestor")
        void subgestor_tambemLe() {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(
                    ApoioAutenticacao.login(http, EMAIL_SUBGESTOR, SENHA_SUBGESTOR).accessToken());

            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/automacao/config", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("valor editado pelo PUT aparece na proxima leitura do GET")
        void valorEditado_apareceNaProximaLeitura() {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken());
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
            http.exchange(
                    "/api/v1/automacao/config/" + CHAVE_TESTE,
                    HttpMethod.PUT,
                    new HttpEntity<>(java.util.Map.of("valor", "77"), cabecalhos),
                    String.class);

            ResponseEntity<String> resposta = listarComoGestor();

            assertThat(resposta.getBody()).contains("\"valor\":\"77\"");
        }

        private ResponseEntity<String> listarComoGestor() {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken());
            return http.exchange(
                    "/api/v1/automacao/config", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
        }
    }

    @Nested
    @DisplayName("feature flags")
    class Flags {

        private static final String CHAVE_FLAG = "e07-flag-de-teste";

        @BeforeEach
        void prepararFlag() {
            jdbc.update("DELETE FROM feature_flag WHERE chave = ?", CHAVE_FLAG);
            jdbc.update(
                    "INSERT INTO feature_flag (chave, habilitado, descricao) VALUES (?, TRUE, 'flag de teste E07')",
                    CHAVE_FLAG);
        }

        @Test
        @DisplayName("flag desligada some de /api/v1/config/features")
        void flagDesligada_someDeConfigFeatures() {
            assertThat(featuresHabilitadas()).contains(CHAVE_FLAG);

            jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = ?", CHAVE_FLAG);

            assertThat(featuresHabilitadas()).doesNotContain(CHAVE_FLAG);
        }

        private String featuresHabilitadas() {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken());
            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/config/features", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            return resposta.getBody();
        }
    }

    // --- apoio ------------------------------------------------------------

    private ResponseEntity<String> comToken(String token, String url) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", token);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    private static HttpEntity<Void> semCabecalhos() {
        return new HttpEntity<>(new HttpHeaders());
    }
}
