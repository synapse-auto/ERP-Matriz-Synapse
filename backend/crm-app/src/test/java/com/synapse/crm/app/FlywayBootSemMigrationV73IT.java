package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Prova que o CRM sobe saudável em schema72 sem executar a V73 ou alterar dados históricos. */
class FlywayBootSemMigrationV73IT extends PostgresIT {

    private static final String BANCO = "synapse_v73_boot_it";
    private static final String LEAD_IMPORTADO = "00000000-0000-4000-8000-000000000081";
    private static final String LEAD_COM_CONVERSA = "00000000-0000-4000-8000-000000000082";
    private static final String URL_BANCO = POSTGRES.getJdbcUrl().replace(POSTGRES.getDatabaseName(), BANCO);

    static {
        prepararBancoEm72();
    }

    @Test
    @DisplayName("boot normal responde liveness/readiness sem fundir leads nem aplicar V73")
    void bootNormalComV73Pendente_permaneceSaudavelSemBackfill() throws Exception {
        assertThat(contarLeadsSinteticos()).isEqualTo(2);
        assertThat(contarV73()).isZero();

        try (ConfigurableApplicationContext contexto = iniciarCrmNormal()) {
            int porta = ((ServletWebServerApplicationContext) contexto)
                    .getWebServer()
                    .getPort();
            HttpClient http = HttpClient.newHttpClient();

            assertThat(status(http, porta, "/health/liveness")).isEqualTo(200);
            assertThat(status(http, porta, "/health/readiness")).isEqualTo(200);
        }

        assertThat(contarV73()).isZero();
        assertThat(contarLeadsSinteticos()).isEqualTo(2);
    }

    private static ConfigurableApplicationContext iniciarCrmNormal() {
        String[] argumentos = {
            "--server.port=0",
            "--synapse.datasource.general.url=" + URL_BANCO,
            "--synapse.datasource.general.username=" + POSTGRES.getUsername(),
            "--synapse.datasource.general.password=" + POSTGRES.getPassword(),
            "--synapse.datasource.general.hikari.maximum-pool-size=2",
            "--synapse.datasource.general.hikari.minimum-idle=0",
            "--synapse.datasource.chat.url=" + URL_BANCO,
            "--synapse.datasource.chat.username=" + POSTGRES.getUsername(),
            "--synapse.datasource.chat.password=" + POSTGRES.getPassword(),
            "--synapse.datasource.chat.hikari.maximum-pool-size=2",
            "--synapse.datasource.chat.hikari.minimum-idle=0",
            "--spring.data.redis.host=" + REDIS.getHost(),
            "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
            "--synapse.seguranca.jwt-segredo=" + SEGREDO_JWT_DE_TESTE,
            "--synapse.canal.whatsapp.provedor=fake",
            "--synapse.canal.outbox.intervalo-ms=3600000",
            "--synapse.canal.webhook.intervalo-ms=3600000",
            "--synapse.agendamento.habilitado=false",
            "--synapse.saude.critica.monitoramento-habilitado=false",
            "--spring.rabbitmq.listener.simple.auto-startup=false"
        };
        return new SpringApplicationBuilder(SynapseCrmApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(argumentos);
    }

    private static int status(HttpClient http, int porta, String caminho) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .GET()
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static void prepararBancoEm72() {
        try (Connection conexao = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute("CREATE DATABASE " + BANCO);
            Flyway.configure()
                    .dataSource(URL_BANCO, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration")
                    .target("72")
                    .baselineOnMigrate(true)
                    .placeholders(Map.of("telefone_ddi_padrao", "55"))
                    .load()
                    .migrate();
            inserirLeadsSinteticos();
        } catch (Exception erro) {
            throw new IllegalStateException("Não foi possível preparar o schema72 isolado do IT", erro);
        }
    }

    private static void inserirLeadsSinteticos() throws Exception {
        try (Connection conexao = DriverManager.getConnection(URL_BANCO, POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.executeUpdate("INSERT INTO lead (id, nome, telefone) VALUES "
                    + "('" + LEAD_IMPORTADO + "', 'lead sintetico importado', '061999999999'), "
                    + "('" + LEAD_COM_CONVERSA + "', 'lead sintetico com conversa', '5561999999999')");
            comando.executeUpdate("INSERT INTO atendimento (id, lead_id) VALUES "
                    + "('00000000-0000-4000-8000-000000000083', '" + LEAD_COM_CONVERSA + "')");
        }
    }

    private static int contarLeadsSinteticos() {
        return contar("SELECT count(*) FROM lead WHERE id IN ('" + LEAD_IMPORTADO + "', '" + LEAD_COM_CONVERSA + "')");
    }

    private static int contarV73() {
        return contar("SELECT count(*) FROM flyway_schema_history WHERE version = '73'");
    }

    private static int contar(String sql) {
        try (Connection conexao = DriverManager.getConnection(URL_BANCO, POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement();
                var resultado = comando.executeQuery(sql)) {
            resultado.next();
            return resultado.getInt(1);
        } catch (Exception erro) {
            throw new IllegalStateException("Não foi possível consultar o fixture do IT", erro);
        }
    }
}
