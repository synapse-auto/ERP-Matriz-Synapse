package com.synapse.crm.app.saude;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Suite isolada porque mata conexoes reais dos dois pools do ApplicationContext. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.canal.whatsapp.provedor=fake")
class SaudeBancoIndisponivelIT extends PostgresIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    @Qualifier(Pools.CHAT_DATA_SOURCE) private DataSource chat;

    @Autowired
    @Qualifier(Pools.GENERAL_DATA_SOURCE) private DataSource geral;

    @AfterEach
    void restaurarBanco() throws Exception {
        executarNoPostgres(
                "ALTER DATABASE \"" + POSTGRES.getDatabaseName() + "\" WITH ALLOW_CONNECTIONS true;");
        ((HikariDataSource) chat).getHikariPoolMXBean().softEvictConnections();
        ((HikariDataSource) geral).getHikariPoolMXBean().softEvictConnections();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            try (var conexao = chat.getConnection()) {
                return conexao.isValid(1);
            }
        });
    }

    @Test
    @DisplayName("Postgres real indisponível derruba critical, mas não o liveness")
    void postgresRealDerrubado_identificaBancoSemContaminarLiveness() throws Exception {
        executarNoPostgres(
                "ALTER DATABASE \"" + POSTGRES.getDatabaseName() + "\" WITH ALLOW_CONNECTIONS false;");
        executarNoPostgres(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                        + POSTGRES.getDatabaseName()
                        + "';");

        var critical = http.getForEntity("/health/critical", String.class);
        assertThat(critical.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode corpo = JSON.readTree(critical.getBody());
        JsonNode banco = java.util.stream.StreamSupport.stream(
                        corpo.path("componentes").spliterator(), false)
                .filter(c -> "banco-chat".equals(c.path("nome").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(banco.path("status").asText()).isEqualTo("DOWN");

        var liveness = http.getForEntity("/health/liveness", String.class);
        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody()).contains("UP");
    }

    private static void executarNoPostgres(String sql) throws Exception {
        var resultado = POSTGRES.execInContainer(
                "psql", "-U", POSTGRES.getUsername(), "-d", "postgres", "-c", sql);
        if (resultado.getExitCode() != 0) {
            throw new IllegalStateException(resultado.getStderr());
        }
    }
}
