package com.synapse.crm.app.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

/**
 * E196: catalogo de etapas para a Automacao, escrita de etapa do lead e escrita de data de
 * nascimento, todas via {@code /internal/v1}.
 *
 * <p>As etapas usadas sao as do seed de dev ({@code R__seed_dev.sql}) — {@code ordem} e unico na
 * tabela, entao o teste reaproveita os UUIDs conhecidos em vez de inserir etapas proprias.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=segredo-de-teste-e196")
class EtapaEDataNascimentoAutomacaoIT extends PostgresIT {

    private static final String TOKEN_VALIDO = "segredo-de-teste-e196";
    private static final String PREFIXO = "E196-";
    private static final String ROTA_ETAPAS = "/internal/v1/etapas";

    private static final UUID ETAPA_NOVO_CONTATO =
            UUID.fromString("e1000000-0000-4000-8000-000000000001");
    private static final UUID ETAPA_QUALIFICACAO =
            UUID.fromString("e1000000-0000-4000-8000-000000000002");
    private static final UUID ETAPA_PROPOSTA =
            UUID.fromString("e1000000-0000-4000-8000-000000000003");

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM comando_automacao_lead_idempotencia WHERE lead_id IN "
                        + "(SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM campo_customizado WHERE chave = 'data_nascimento'");
    }

    @Nested
    @DisplayName("catalogo de etapas")
    class Catalogo {

        @Test
        @DisplayName("devolve o catalogo do tenant, incluindo as etapas do seed")
        void devolveOCatalogo() {
            ResponseEntity<String> resposta = comToken(HttpMethod.GET, ROTA_ETAPAS, null, null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody())
                    .contains(ETAPA_NOVO_CONTATO.toString())
                    .contains(ETAPA_QUALIFICACAO.toString())
                    .contains("\"resultado\"");
        }
    }

    @Nested
    @DisplayName("escrita de etapa do lead")
    class EscritaDeEtapa {

        @Test
        @DisplayName("etapaId valido muda a etapa e publica ETAPA_ALTERADA com origem AUTOMACAO")
        void etapaValidaMudaEPublicaEvento() {
            UUID lead = criarLead("etapa-valida", ETAPA_NOVO_CONTATO);

            ResponseEntity<String> resposta = comToken(
                    HttpMethod.POST,
                    "/internal/v1/leads/" + lead + "/etapa",
                    "etapa-e196-1",
                    Map.of("etapaId", ETAPA_QUALIFICACAO.toString()));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).contains("\"alterado\":true");
            assertThat(jdbc.queryForObject(
                            "SELECT etapa_atendimento_id FROM lead WHERE id = ?", UUID.class, lead))
                    .isEqualTo(ETAPA_QUALIFICACAO);

            Map<String, Object> linha = jdbc.queryForMap(
                    "SELECT origem, ator_id, dados ->> 'etapa_anterior_id' AS anterior, "
                            + "dados ->> 'etapa_nova_id' AS nova "
                            + "FROM evento_timeline WHERE lead_id = ? AND tipo = 'ETAPA_ALTERADA'",
                    lead);
            assertThat(linha.get("origem")).isEqualTo("AUTOMACAO");
            assertThat(linha.get("ator_id")).isNull();
            assertThat(linha.get("anterior")).isEqualTo(ETAPA_NOVO_CONTATO.toString());
            assertThat(linha.get("nova")).isEqualTo(ETAPA_QUALIFICACAO.toString());
        }

        @Test
        @DisplayName("etapaId inexistente nao altera nada e devolve 422")
        void etapaInexistenteNaoAlteraNada() {
            UUID lead = criarLead("etapa-invalida", ETAPA_NOVO_CONTATO);
            UUID inexistente = UUID.randomUUID();

            ResponseEntity<String> resposta = comToken(
                    HttpMethod.POST,
                    "/internal/v1/leads/" + lead + "/etapa",
                    "etapa-e196-2",
                    Map.of("etapaId", inexistente.toString()));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(jdbc.queryForObject(
                            "SELECT etapa_atendimento_id FROM lead WHERE id = ?", UUID.class, lead))
                    .isEqualTo(ETAPA_NOVO_CONTATO);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM evento_timeline WHERE lead_id = ? AND tipo = 'ETAPA_ALTERADA'",
                            Integer.class,
                            lead))
                    .isZero();
        }

        @Test
        @DisplayName("retry com a mesma Idempotency-Key nao duplica o evento nem falha")
        void retryComMesmaChaveNaoDuplica() {
            UUID lead = criarLead("etapa-retry", ETAPA_NOVO_CONTATO);
            Map<String, Object> corpo = Map.of("etapaId", ETAPA_PROPOSTA.toString());

            ResponseEntity<String> primeira = comToken(
                    HttpMethod.POST, "/internal/v1/leads/" + lead + "/etapa", "etapa-e196-retry", corpo);
            ResponseEntity<String> retry = comToken(
                    HttpMethod.POST, "/internal/v1/leads/" + lead + "/etapa", "etapa-e196-retry", corpo);

            assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(retry.getBody()).isEqualTo(primeira.getBody());
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM evento_timeline WHERE lead_id = ? AND tipo = 'ETAPA_ALTERADA'",
                            Integer.class,
                            lead))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("escrita de data de nascimento")
    class EscritaDeDataNascimento {

        @Test
        @DisplayName("lead sem valor previo grava")
        void leadSemValorGrava() {
            cadastrarCampoDeNascimento();
            UUID lead = criarLead("nascimento-vazio", null);

            ResponseEntity<String> resposta = comToken(
                    HttpMethod.POST,
                    "/internal/v1/leads/" + lead + "/data-nascimento",
                    "nascimento-e196-1",
                    Map.of("dataNascimento", "1990-05-21"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).contains("\"situacao\":\"APLICADO\"");
            assertThat(jdbc.queryForObject(
                            "SELECT dados_customizados ->> 'data_nascimento' FROM lead WHERE id = ?",
                            String.class,
                            lead))
                    .startsWith("1990-05-21");
        }

        @Test
        @DisplayName("lead com valor ja preenchido nao e sobrescrito")
        void leadComValorNaoESobrescrito() {
            cadastrarCampoDeNascimento();
            UUID lead = criarLeadComNascimento("nascimento-preenchido", "1985-01-02T00:00:00Z");

            ResponseEntity<String> resposta = comToken(
                    HttpMethod.POST,
                    "/internal/v1/leads/" + lead + "/data-nascimento",
                    "nascimento-e196-2",
                    Map.of("dataNascimento", "1999-12-31"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).contains("\"situacao\":\"IGNORADO_JA_PREENCHIDO\"");
            assertThat(jdbc.queryForObject(
                            "SELECT dados_customizados ->> 'data_nascimento' FROM lead WHERE id = ?",
                            String.class,
                            lead))
                    .isEqualTo("1985-01-02T00:00:00Z");
        }

        @Test
        @DisplayName("tenant sem o campo customizado data_nascimento recusa com erro claro, sem gravar")
        void semCampoCustomizadoRecusa() {
            UUID lead = criarLead("nascimento-sem-campo", null);

            ResponseEntity<String> resposta = comToken(
                    HttpMethod.POST,
                    "/internal/v1/leads/" + lead + "/data-nascimento",
                    "nascimento-e196-3",
                    Map.of("dataNascimento", "1990-05-21"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(jdbc.queryForObject(
                            "SELECT dados_customizados ->> 'data_nascimento' FROM lead WHERE id = ?",
                            String.class,
                            lead))
                    .isNull();
        }

        @Test
        @DisplayName("data em formato invalido e rejeitada antes de tocar o banco")
        void formatoInvalidoERejeitado() {
            cadastrarCampoDeNascimento();
            UUID lead = criarLead("nascimento-invalido", null);

            ResponseEntity<String> resposta = comToken(
                    HttpMethod.POST,
                    "/internal/v1/leads/" + lead + "/data-nascimento",
                    "nascimento-e196-4",
                    Map.of("dataNascimento", "31 de dezembro"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(jdbc.queryForObject(
                            "SELECT dados_customizados ->> 'data_nascimento' FROM lead WHERE id = ?",
                            String.class,
                            lead))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("autenticacao do contrato interno")
    class Autenticacao {

        @Test
        @DisplayName("sem token, os tres endpoints novos devolvem 401")
        void semTokenDevolve401() {
            UUID lead = criarLead("sem-token", ETAPA_NOVO_CONTATO);

            assertThat(semToken(HttpMethod.GET, ROTA_ETAPAS, null, null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(semToken(
                            HttpMethod.POST,
                            "/internal/v1/leads/" + lead + "/etapa",
                            "sem-token-etapa",
                            Map.of("etapaId", ETAPA_QUALIFICACAO.toString()))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(semToken(
                            HttpMethod.POST,
                            "/internal/v1/leads/" + lead + "/data-nascimento",
                            "sem-token-nascimento",
                            Map.of("dataNascimento", "1990-05-21"))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- apoio ----------------------------------------------------------------

    private void cadastrarCampoDeNascimento() {
        jdbc.update(
                """
                INSERT INTO campo_customizado (chave, rotulo, tipo, obrigatorio, filtravel, ordem)
                VALUES ('data_nascimento', 'Data de nascimento', 'DATA', false, false, 0)
                ON CONFLICT (chave) DO NOTHING
                """);
    }

    private UUID criarLead(String nome, UUID etapaId) {
        return inserirLead(nome, etapaId, "{}");
    }

    private UUID criarLeadComNascimento(String nome, String dataNascimentoIso) {
        return inserirLead(
                nome, null, "{\"data_nascimento\": \"" + dataNascimentoIso + "\"}");
    }

    private UUID inserirLead(String nome, UUID etapaId, String dadosCustomizados) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO lead (id, nome, telefone, status_basico, etapa_atendimento_id, ultima_interacao_em, dados_customizados)
                VALUES (?, ?, ?, 'IA', ?, now(), ?::jsonb)
                """,
                id,
                PREFIXO + nome,
                telefoneDe(nome),
                etapaId,
                dadosCustomizados);
        return id;
    }

    private static String telefoneDe(String nome) {
        return "556198%07d".formatted(Math.abs(nome.hashCode()) % 10_000_000);
    }

    private ResponseEntity<String> comToken(HttpMethod metodo, String rota, String chave, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", TOKEN_VALIDO);
        if (chave != null) {
            cabecalhos.set("Idempotency-Key", chave);
        }
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(rota, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private ResponseEntity<String> semToken(HttpMethod metodo, String rota, String chave, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (chave != null) {
            cabecalhos.set("Idempotency-Key", chave);
        }
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(rota, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
