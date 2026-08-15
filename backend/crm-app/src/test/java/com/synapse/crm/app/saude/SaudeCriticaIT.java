package com.synapse.crm.app.saude;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

/** Falhas reais no Postgres, na fila transacional e na credencial persistida. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.saude.critica.fila-sem-consumo-maximo=5m"
        })
class SaudeCriticaIT extends PostgresIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PublicadorDaOutbox consumidor;

    private String nomeCredencialAtiva;
    private String nomeOutbox;
    private List<UUID> canaisAtivosAntes;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM outbox_evento WHERE tipo = 'e22.saude.teste'");
        consumidor.publicarPendentes();
        nomeCredencialAtiva = "canal_credencial_e22_" + UUID.randomUUID().toString().replace("-", "");
        nomeOutbox = "outbox_evento_e22_" + UUID.randomUUID().toString().replace("-", "");
        canaisAtivosAntes = jdbc.queryForList("SELECT id FROM canal WHERE ativo", UUID.class);
    }

    @AfterEach
    void restaurarEstruturas() throws Exception {
        habilitarBanco();
        if (tabelaExiste(nomeOutbox)) {
            jdbc.execute("ALTER TABLE " + nomeOutbox + " RENAME TO outbox_evento");
        }
        if (tabelaExiste(nomeCredencialAtiva)) {
            jdbc.execute("ALTER TABLE " + nomeCredencialAtiva + " RENAME TO canal_credencial");
        }
        for (UUID canalId : canaisAtivosAntes) {
            jdbc.update("UPDATE canal SET ativo = TRUE WHERE id = ?", canalId);
        }
        jdbc.update("DELETE FROM outbox_evento WHERE tipo = 'e22.saude.teste'");
    }

    @Test
    @DisplayName("saude normal comprova os seis componentes")
    void todosOsComponentesSaudaveis() throws Exception {
        JsonNode corpo = chamarCritical().corpo();

        assertThat(corpo.path("status").asText()).isEqualTo("UP");
        assertThat(corpo.path("componentes")).hasSize(6);
        assertThat(nomes(corpo)).containsExactlyInAnyOrder(
                "banco-chat",
                "fila-outbox",
                "canal",
                "websocket",
                "particoes-mensagem",
                "acumulo-outbox");
    }

    @Test
    @DisplayName("derrubar a fila real identifica fila-outbox")
    void filaRealDerrubada_identificaComponente() throws Exception {
        jdbc.execute("ALTER TABLE outbox_evento RENAME TO " + nomeOutbox);

        // O ponto de entrada que o scheduler chama falha contra a tabela real ausente e, portanto,
        // nao atualiza o heartbeat do consumidor.
        org.assertj.core.api.Assertions.assertThatThrownBy(consumidor::publicarPendentes)
                .isInstanceOf(RuntimeException.class);
        Resposta resposta = chamarCritical();
        assertThat(resposta.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(componente(resposta.corpo(), "fila-outbox").path("status").asText())
                .isEqualTo("DOWN");
    }

    @Test
    @DisplayName("credencial ativa ausente identifica canal")
    void credencialRealInvalida_identificaCanal() throws Exception {
        jdbc.execute("ALTER TABLE canal_credencial RENAME TO " + nomeCredencialAtiva);

        Resposta resposta = chamarCritical();
        assertThat(resposta.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(componente(resposta.corpo(), "canal").path("status").asText())
                .isEqualTo("DOWN");
    }

    @Test
    @DisplayName("nenhum canal ativo cadastrado derruba a saude critica")
    void canalAtivoAusente_identificaCanal() throws Exception {
        jdbc.update("UPDATE canal SET ativo = FALSE");

        Resposta resposta = chamarCritical();
        assertThat(resposta.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode canal = componente(resposta.corpo(), "canal");
        assertThat(canal.path("status").asText()).isEqualTo("DOWN");
        assertThat(canal.path("detalhe").asText()).isEqualTo("nenhum canal ativo cadastrado");
    }

    @Test
    @DisplayName("acúmulo real da outbox degrada sem declarar queda crítica")
    void acumuloRealDaOutbox_respondeDegraded() throws Exception {
        jdbc.update(
                """
                INSERT INTO outbox_evento
                    (tipo, payload, criado_em, proxima_tentativa_em)
                SELECT 'e22.saude.teste', '{}'::jsonb, now() - interval '10 minutes', now()
                  FROM generate_series(1, 21)
                """);

        Resposta resposta = chamarCritical();
        assertThat(resposta.status()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.corpo().path("status").asText()).isEqualTo("DEGRADED");
        assertThat(componente(resposta.corpo(), "acumulo-outbox").path("status").asText())
                .isEqualTo("DOWN");
    }

    private Resposta chamarCritical() throws Exception {
        var resposta = http.getForEntity("/health/critical", String.class);
        return new Resposta(resposta.getStatusCode(), JSON.readTree(resposta.getBody()));
    }

    private boolean tabelaExiste(String nome) {
        if (nome == null) {
            return false;
        }
        Boolean existe = jdbc.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, nome);
        return Boolean.TRUE.equals(existe);
    }

    private static java.util.List<String> nomes(JsonNode corpo) {
        java.util.List<String> nomes = new java.util.ArrayList<>();
        corpo.path("componentes").forEach(c -> nomes.add(c.path("nome").asText()));
        return nomes;
    }

    private static JsonNode componente(JsonNode corpo, String nome) {
        for (JsonNode componente : corpo.path("componentes")) {
            if (nome.equals(componente.path("nome").asText())) {
                return componente;
            }
        }
        throw new AssertionError("componente ausente: " + nome);
    }

    private static void habilitarBanco() throws Exception {
        executarNoPostgres(
                "ALTER DATABASE \"" + POSTGRES.getDatabaseName() + "\" WITH ALLOW_CONNECTIONS true;");
    }

    private static void executarNoPostgres(String sql) throws Exception {
        var resultado = POSTGRES.execInContainer(
                "psql", "-U", POSTGRES.getUsername(), "-d", "postgres", "-c", sql);
        if (resultado.getExitCode() != 0) {
            throw new IllegalStateException(resultado.getStderr());
        }
    }

    private record Resposta(HttpStatusCode status, JsonNode corpo) {}
}
