package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Campos customizados por instancia (E06b): schema, leitura/escrita validada e integracao com o
 * filtro modular dinamico.
 *
 * <p>A suite cadastra os campos direto no banco (via {@code jdbc}), reproduzindo a fase 1 descrita na
 * migration V18 — a tela de gestao e fase 2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class CampoCustomizadoIT extends PostgresIT {

    private static final String CAMPO_TEXTO_FILTRAVEL = "e06b_numero_obra";
    private static final String CAMPO_NUMERO_NAO_FILTRAVEL = "e06b_qtd_vidros";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID leadDaAna;

    @BeforeEach
    void prepararCenario() {
        jdbc.update("DELETE FROM lead WHERE nome LIKE 'E06B-%'");
        jdbc.update(
                "DELETE FROM campo_customizado WHERE chave IN (?, ?)",
                CAMPO_TEXTO_FILTRAVEL,
                CAMPO_NUMERO_NAO_FILTRAVEL);

        jdbc.update(
                """
                INSERT INTO campo_customizado (chave, rotulo, tipo, obrigatorio, filtravel, ordem)
                VALUES (?, 'Numero da obra', 'TEXTO', false, true, 1)
                """,
                CAMPO_TEXTO_FILTRAVEL);
        jdbc.update(
                """
                INSERT INTO campo_customizado (chave, rotulo, tipo, obrigatorio, filtravel, ordem)
                VALUES (?, 'Quantidade de vidros', 'NUMERO', false, false, 2)
                """,
                CAMPO_NUMERO_NAO_FILTRAVEL);

        UUID idAna = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        leadDaAna = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico) "
                        + "VALUES (?, 'E06B-Cliente da Ana', ?, 'EM_ATENDIMENTO')",
                leadDaAna,
                idAna);
    }

    @Test
    @DisplayName("campo cadastrado aparece na listagem de campos customizados")
    void listagemDeCampos_campoCadastrado_aparece() {
        String corpo = comoAna(HttpMethod.GET, "/api/v1/campos-customizados", null).getBody();

        assertThat(corpo)
                .contains(CAMPO_TEXTO_FILTRAVEL)
                .contains("Numero da obra")
                .contains("TEXTO")
                .contains(CAMPO_NUMERO_NAO_FILTRAVEL);
    }

    @Test
    @DisplayName("lead salva e le dados_customizados")
    void ficha_salvaELeDadosCustomizados() {
        var resposta = comoAna(
                HttpMethod.PUT,
                "/api/v1/leads/" + leadDaAna,
                Map.of("dadosCustomizados", Map.of(CAMPO_TEXTO_FILTRAVEL, "OBRA-4521")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("OBRA-4521");

        String corpoFicha = comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDaAna, null).getBody();
        assertThat(corpoFicha).contains("OBRA-4521");
    }

    @Test
    @DisplayName("chave nao cadastrada e rejeitada no PUT")
    void editar_chaveNaoCadastrada_devolve400() {
        var resposta = comoAna(
                HttpMethod.PUT,
                "/api/v1/leads/" + leadDaAna,
                Map.of("dadosCustomizados", Map.of("campo_fantasma", "x")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("nao cadastrado");
    }

    @Test
    @DisplayName("tipo incompativel e rejeitado")
    void editar_tipoIncompativel_devolve400() {
        var resposta = comoAna(
                HttpMethod.PUT,
                "/api/v1/leads/" + leadDaAna,
                Map.of("dadosCustomizados", Map.of(CAMPO_NUMERO_NAO_FILTRAVEL, "nao-e-numero")));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("numero inteiro");
    }

    @Test
    @DisplayName("listagem de leads nao traz dados_customizados")
    void listagem_naoTrazDadosCustomizados() {
        comoAna(
                HttpMethod.PUT,
                "/api/v1/leads/" + leadDaAna,
                Map.of("dadosCustomizados", Map.of(CAMPO_TEXTO_FILTRAVEL, "OBRA-SIGILOSA")));

        String corpo = comoAna(HttpMethod.GET, "/api/v1/leads", null).getBody();

        assertThat(corpo).contains("E06B-Cliente da Ana");
        assertThat(corpo).doesNotContain("OBRA-SIGILOSA").doesNotContain("dadosCustomizados");
    }

    @Nested
    @DisplayName("filtro modular dinamico")
    class FiltroDinamico {

        @BeforeEach
        void marcarLeadComCampoCustomizado() {
            comoAna(
                    HttpMethod.PUT,
                    "/api/v1/leads/" + leadDaAna,
                    Map.of("dadosCustomizados", Map.of(CAMPO_TEXTO_FILTRAVEL, "OBRA-9001")));
        }

        @Test
        @DisplayName("campo filtravel funciona no filtro modular")
        void filtrar_porCampoFiltravel_encontraOLead() {
            ResponseEntity<String> resposta =
                    filtrar(simples(CAMPO_TEXTO_FILTRAVEL, "IGUAL", List.of("OBRA-9001")));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).contains(leadDaAna.toString());
        }

        @Test
        @DisplayName("campo customizado nao filtravel e rejeitado")
        void filtrar_porCampoNaoFiltravel_devolve400() {
            ResponseEntity<String> resposta =
                    filtrar(simples(CAMPO_NUMERO_NAO_FILTRAVEL, "IGUAL", List.of("1")));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody()).contains("campo nao permitido");
        }

        /**
         * A chave so vira caminho JSONB depois de resolvida contra {@code campo_customizado} — uma
         * chave forjada, nao cadastrada, nunca chega la. O 400 aqui e a mesma allowlist da E03b, so
         * que aplicada a parte dinamica.
         */
        @Test
        @DisplayName("injecao pela chave de campo customizado nao executa SQL arbitrario")
        void filtrar_porChaveForjada_naoExecutaSql() {
            ResponseEntity<String> resposta =
                    filtrar(simples("chave'); DROP TABLE lead; --", "IGUAL", List.of("x")));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody()).contains("campo nao permitido");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM lead", Integer.class)).isPositive();
        }

        /**
         * Segunda parede: mesmo um INSERT direto no banco (fora da aplicacao) nao cria uma chave fora
         * do formato seguro — o {@code CHECK chk_campo_customizado_chave} da V18 barra antes que essa
         * chave pudesse um dia virar caminho JSONB.
         */
        @Test
        @DisplayName("chave com sintaxe maliciosa nao passa nem pelo CHECK do banco")
        void inserirCampoCustomizado_chaveMaliciosa_violaCheckDoBanco() {
            assertThatThrownBy(() -> jdbc.update(
                            """
                            INSERT INTO campo_customizado (chave, rotulo, tipo)
                            VALUES (?, 'Malicioso', 'TEXTO')
                            """,
                            "chave'); drop table lead; --"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // --- apoio ------------------------------------------------------------

    private static Map<String, Object> simples(String campo, String operador, List<String> valores) {
        Map<String, Object> criterio = new HashMap<>();
        criterio.put("tipo", "SIMPLES");
        criterio.put("campo", campo);
        criterio.put("operador", operador);
        criterio.put("valores", new ArrayList<>(valores));
        return Map.of("criterio", criterio);
    }

    private ResponseEntity<String> filtrar(Map<String, Object> envelope) {
        return comoAna(HttpMethod.POST, "/api/v1/leads/filtrar", envelope);
    }

    private ResponseEntity<String> comoAna(HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
