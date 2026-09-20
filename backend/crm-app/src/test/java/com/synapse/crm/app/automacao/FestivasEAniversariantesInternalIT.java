package com.synapse.crm.app.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;

/**
 * E194: as duas pontes que faltavam para a Automacao — a data festiva de hoje e os aniversariantes
 * de hoje.
 *
 * <p>As datas sao calculadas a partir de hoje no fuso da instancia, o mesmo que o servidor usa,
 * entao o teste nao depende do dia em que o CI rodou nem quebra na virada do ano. A regra de mes e
 * dia em si esta coberta por teste unitario com relogio fixo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=segredo-de-teste-do-internal-v1")
class FestivasEAniversariantesInternalIT extends PostgresIT {

    private static final String TOKEN_VALIDO = "segredo-de-teste-do-internal-v1";
    private static final String PREFIXO = "E194-";
    private static final String ROTA_FESTIVAS = "/internal/v1/mensagens-festivas/hoje";
    private static final String ROTA_ANIVERSARIANTES = "/internal/v1/fidelizacao/aniversariantes-hoje";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ZoneId fusoDoTenant;

    private LocalDate hoje;

    @BeforeEach
    void limparEPreparar() {
        limpar();
        hoje = LocalDate.now(fusoDoTenant);
    }

    @AfterEach
    void limparDepois() {
        limpar();
    }

    private void limpar() {
        jdbc.update("DELETE FROM mensagem_festiva WHERE titulo LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM campo_customizado WHERE chave = 'data_nascimento'");
        jdbc.update(
                "UPDATE configuracao_automacao SET valor = 'false' WHERE chave = 'fidelizacao.aniversario.habilitado'");
    }

    @Nested
    @DisplayName("mensagens festivas de hoje")
    class Festivas {

        @Test
        @DisplayName("ativa de hoje aparece mesmo cadastrada em outro ano; inativa e de outro dia nao")
        void devolveSomenteAsAtivasDeHoje() {
            criarFestiva("ativa de outro ano", hoje.minusYears(3), true);
            criarFestiva("inativa de hoje", hoje, false);
            criarFestiva("ativa de outro dia", hoje.plusDays(1), true);

            ResponseEntity<String> resposta = comToken(ROTA_FESTIVAS);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody())
                    .contains(PREFIXO + "ativa de outro ano")
                    .doesNotContain(PREFIXO + "inativa de hoje")
                    .doesNotContain(PREFIXO + "ativa de outro dia");
        }

        @Test
        @DisplayName("dia sem data festiva devolve 200 com lista vazia, nunca 404")
        void diaSemDataFestivaNaoE404() {
            criarFestiva("de outro dia", hoje.plusDays(2), true);

            ResponseEntity<String> resposta = comToken(ROTA_FESTIVAS);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).doesNotContain(PREFIXO);
        }
    }

    @Nested
    @DisplayName("aniversariantes de hoje")
    class Aniversariantes {

        @Test
        @DisplayName("desabilitado devolve lista vazia mesmo com aniversariante do dia")
        void desabilitadoZeraALista() {
            cadastrarCampoDeNascimento();
            criarLead("aniversariante", hoje.minusYears(30));

            ResponseEntity<String> resposta = comToken(ROTA_ANIVERSARIANTES);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).contains("\"habilitado\":false").contains("\"leads\":[]");
        }

        @Test
        @DisplayName("habilitado lista quem faz aniversario hoje, em qualquer ano, e ignora os demais")
        void habilitadoListaSomenteOsDoDia() {
            habilitar();
            cadastrarCampoDeNascimento();
            criarLead("aniversariante", hoje.minusYears(30));
            criarLead("de outro dia", hoje.plusDays(3).minusYears(20));
            criarLeadSemNascimento("sem data");
            criarLeadComNascimentoInvalido("data invalida");

            ResponseEntity<String> resposta = comToken(ROTA_ANIVERSARIANTES);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody())
                    .contains("\"habilitado\":true")
                    .contains(PREFIXO + "aniversariante")
                    .contains(telefoneDe("aniversariante"))
                    .doesNotContain(PREFIXO + "de outro dia")
                    .doesNotContain(PREFIXO + "sem data")
                    .doesNotContain(PREFIXO + "data invalida");
        }

        @Test
        @DisplayName("instancia sem o campo customizado data_nascimento devolve lista vazia, sem erro")
        void semCampoCustomizadoNaoQuebra() {
            habilitar();
            criarLead("aniversariante", hoje.minusYears(30));

            ResponseEntity<String> resposta = comToken(ROTA_ANIVERSARIANTES);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).contains("\"habilitado\":true").contains("\"leads\":[]");
        }
    }

    @Nested
    @DisplayName("autenticacao do contrato interno")
    class Autenticacao {

        @Test
        @DisplayName("sem token, os dois endpoints devolvem 401")
        void semTokenDevolve401() {
            assertThat(semToken(ROTA_FESTIVAS).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(semToken(ROTA_ANIVERSARIANTES).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("com token errado, os dois endpoints devolvem 401")
        void tokenErradoDevolve401() {
            assertThat(comTokenErrado(ROTA_FESTIVAS).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(comTokenErrado(ROTA_ANIVERSARIANTES).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- apoio ----------------------------------------------------------------

    private void criarFestiva(String titulo, LocalDate data, boolean ativo) {
        jdbc.update(
                "INSERT INTO mensagem_festiva (id, titulo, icone, data, texto, ativo) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                PREFIXO + titulo,
                "🎉",
                data,
                "Mensagem de teste",
                ativo);
    }

    private void cadastrarCampoDeNascimento() {
        jdbc.update(
                """
                INSERT INTO campo_customizado (chave, rotulo, tipo, obrigatorio, filtravel, ordem)
                VALUES ('data_nascimento', 'Data de nascimento', 'DATA', false, false, 0)
                ON CONFLICT (chave) DO NOTHING
                """);
    }

    private void habilitar() {
        jdbc.update(
                "UPDATE configuracao_automacao SET valor = 'true' WHERE chave = 'fidelizacao.aniversario.habilitado'");
    }

    private void criarLead(String nome, LocalDate nascimento) {
        inserirLead(nome, "{\"data_nascimento\": \"" + nascimento + "\"}");
    }

    private void criarLeadSemNascimento(String nome) {
        inserirLead(nome, "{}");
    }

    private void criarLeadComNascimentoInvalido(String nome) {
        // Importacao malfeita nao pode derrubar a consulta inteira: a linha so nao entra na lista.
        inserirLead(nome, "{\"data_nascimento\": \"quinta-feira\"}");
    }

    /** Telefone e unico em lead (ux_lead_telefone), entao cada lead do teste recebe o seu. */
    private void inserirLead(String nome, String dadosCustomizados) {
        jdbc.update(
                """
                INSERT INTO lead (id, nome, telefone, status_basico, ultima_interacao_em, dados_customizados)
                VALUES (?, ?, ?, 'IA', now(), ?::jsonb)
                """,
                UUID.randomUUID(),
                PREFIXO + nome,
                telefoneDe(nome),
                dadosCustomizados);
    }

    private static String telefoneDe(String nome) {
        return "556199%07d".formatted(Math.abs(nome.hashCode()) % 10_000_000);
    }

    private ResponseEntity<String> comToken(String rota) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", TOKEN_VALIDO);
        return http.exchange(rota, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    private ResponseEntity<String> comTokenErrado(String rota) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", "token-que-nao-bate");
        return http.exchange(rota, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    private ResponseEntity<String> semToken(String rota) {
        return http.exchange(rota, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
    }
}
