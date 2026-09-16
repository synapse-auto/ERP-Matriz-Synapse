package com.synapse.migrationrunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;
import com.synapse.crm.app.config.FlywayValidacaoNoBootConfig;

/** Testa o mesmo runner em schema72 pendente e schema73 aplicado, usando PostgreSQL real. */
class FlywayMigrationRunnerIT extends PostgresIT {

    private static final String BANCO = "synapse_v73_runner_it";
    private static final PGSimpleDataSource DATA_SOURCE = dataSourceDoBancoIsolado();

    @BeforeAll
    static void criarBancoIsolado() throws Exception {
        try (Connection conexao = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute("CREATE DATABASE " + BANCO);
        }
    }

    @BeforeEach
    void resetarSchemaIsolado() throws Exception {
        try (Connection conexao = DATA_SOURCE.getConnection(); Statement comando = conexao.createStatement()) {
            comando.execute("DROP SCHEMA IF EXISTS public CASCADE");
            comando.execute("CREATE SCHEMA public AUTHORIZATION CURRENT_USER");
            comando.execute("GRANT ALL ON SCHEMA public TO PUBLIC");
        }
    }

    @Test
    @DisplayName("schema já em 73 valida checksum e runner termina sem segunda execução")
    void schemaAtual73_runnerNaoReexecuta() {
        Flyway flyway = novoFlyway(null);
        flyway.migrate();
        int checksumAntes = checksumV73();

        new FlywayValidacaoNoBootConfig().validarSchemaSemMigrarNoBoot().migrate(flyway);
        FlywayMigrationRunner.Resultado resultado = runner(flyway).executar();

        assertThat(resultado.executou()).isFalse();
        assertThat(resultado.migrationsExecutadas()).isZero();
        assertThat(resultado.versaoFinal()).isEqualTo("73");
        assertThat(checksumV73()).isEqualTo(checksumAntes);
        assertThat(contarV73()).isEqualTo(1);
    }

    @Test
    @DisplayName("schema72 é validado sem migrar no boot e o runner executa V73 uma vez")
    void schemaAtual72_runnerExecutaUmaVezEConsolidaDadosSinteticos() throws Exception {
        Flyway ateV72 = novoFlyway("72");
        ateV72.migrate();
        assertThat(versaoAtual(ateV72)).isEqualTo("72");
        inserirParSinteticoDeLeads();
        assertThat(leadExiste("00000000-0000-4000-8000-000000000071")).isTrue();
        assertThat(leadExiste("00000000-0000-4000-8000-000000000072")).isTrue();

        // Exatamente a estratégia do boot normal: V73 continua pendente e não é executada.
        Flyway flywayDoBootNormal = flywayNormalDoBoot();
        new FlywayValidacaoNoBootConfig().validarSchemaSemMigrarNoBoot().migrate(flywayDoBootNormal);
        assertThat(versaoAtual(ateV72)).isEqualTo("72");
        assertThat(leadExiste("00000000-0000-4000-8000-000000000071")).isTrue();
        assertThat(leadExiste("00000000-0000-4000-8000-000000000072")).isTrue();

        Flyway flywayAtual = novoFlyway(null);
        FlywayMigrationRunner.Resultado resultado = runner(flywayAtual).executar();
        FlywayMigrationRunner.Resultado repeticao = runner(flywayAtual).executar();

        assertThat(resultado.executou()).isTrue();
        assertThat(resultado.migrationsExecutadas()).isEqualTo(1);
        assertThat(resultado.versaoFinal()).isEqualTo("73");
        assertThat(repeticao.executou()).isFalse();
        assertThat(contarV73()).isEqualTo(1);
        assertThat(leadExiste("00000000-0000-4000-8000-000000000071")).isFalse();
        assertThat(telefoneDoLead("00000000-0000-4000-8000-000000000072"))
                .isEqualTo("5561999999999");
    }

    @Test
    @DisplayName("execução concorrente falha sem esperar nem iniciar a migration")
    void runnerOutroProcessoJaTemLock_recusaEPermiteTentativaControladaDepois() throws Exception {
        novoFlyway("72").migrate();
        Flyway flywayAtual = novoFlyway(null);

        try (Connection conexaoConcorrente = DATA_SOURCE.getConnection();
                var lock = conexaoConcorrente.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            lock.setInt(1, FlywayMigrationRunner.LOCK_NAMESPACE);
            lock.setInt(2, FlywayMigrationRunner.LOCK_RESOURCE);
            try (var resultado = lock.executeQuery()) {
                assertThat(resultado.next()).isTrue();
                assertThat(resultado.getBoolean(1)).isTrue();
            }

            assertThatThrownBy(() -> runner(flywayAtual).executar())
                    .isInstanceOf(FlywayMigrationRunner.MigracaoJaEmExecucaoException.class);
            assertThat(versaoAtual(flywayAtual)).isEqualTo("72");
        }

        FlywayMigrationRunner.Resultado resultado = runner(flywayAtual).executar();
        assertThat(resultado.executou()).isTrue();
        assertThat(versaoAtual(flywayAtual)).isEqualTo("73");
    }

    @Test
    @DisplayName("runner recusa schema diferente de 72 sem executar versões anteriores pendentes")
    void schemaAnteriorAo72_runnerNaoAplicaNenhumaMigration() {
        Flyway ateV71 = novoFlyway("71");
        ateV71.migrate();

        assertThatThrownBy(() -> runner(novoFlyway(null)).executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Estado do schema não suportado");
        assertThat(versaoAtual(novoFlyway(null))).isEqualTo("71");
        assertThat(contarV73()).isZero();
    }

    @Test
    @DisplayName("comando real inicia somente runner e executa depois do lock")
    void entrypointComFlag_runnerControladoAtualizaV72SemSubirCrm() throws Exception {
        novoFlyway("72").migrate();

        SynapseCrmApplication.main(argumentosDoRunner());

        assertThat(versaoAtual(novoFlyway(null))).isEqualTo("73");
        assertThat(contarV73()).isEqualTo(1);
    }

    @Test
    @DisplayName("timeouts do runner são positivos, finitos e ocultam NOTICE com dados de lead")
    void configuracaoRunner_limitaTempoESuprimeNotices() {
        var customizer = new FlywayMigrationRunnerConfiguration()
                .limitesDoRunner(new MigrationRunnerProperties(Duration.ofSeconds(10), Duration.ofMinutes(30)));
        FluentConfiguration configuracao = Flyway.configure();
        customizer.customize(configuracao);

        assertThat(configuracao.getInitSql())
                .contains("client_min_messages", "warning", "10000ms", "1800000ms");
        assertThat(configuracao.getTarget().getVersion()).isEqualTo("73");
        assertThat(configuracao.getLockRetryCount()).isZero();
        assertThat(configuracao.getLocations())
                .containsExactly(new org.flywaydb.core.api.Location("classpath:db/migration"));
        assertThatThrownBy(() -> new MigrationRunnerProperties(Duration.ZERO, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MigrationRunnerProperties(Duration.ofSeconds(10), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FlywayMigrationRunner runner(Flyway flyway) {
        return new FlywayMigrationRunner(flyway, DATA_SOURCE);
    }

    private static Flyway novoFlyway(String alvo) {
        FluentConfiguration configuracao = Flyway.configure()
                .dataSource(DATA_SOURCE)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .placeholders(Map.of("telefone_ddi_padrao", "55"));
        FlywayConfigurationCustomizer limites = new FlywayMigrationRunnerConfiguration()
                .limitesDoRunner(new MigrationRunnerProperties(Duration.ofSeconds(10), Duration.ofMinutes(30)));
        limites.customize(configuracao);
        // Os fixtures podem criar o estado histórico anterior; a configuração do runner real fica em alvo 73.
        if (alvo != null) {
            configuracao.target(alvo);
        }
        return configuracao.load();
    }

    private static Flyway flywayNormalDoBoot() {
        return Flyway.configure()
                .dataSource(DATA_SOURCE)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .placeholders(Map.of("telefone_ddi_padrao", "55"))
                .load();
    }

    private static PGSimpleDataSource dataSourceDoBancoIsolado() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl().replace(POSTGRES.getDatabaseName(), BANCO));
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static String[] argumentosDoRunner() {
        String url = DATA_SOURCE.getUrl();
        String usuario = POSTGRES.getUsername();
        String senha = POSTGRES.getPassword();
        return new String[] {
            SynapseCrmApplication.ARGUMENTO_RUNNER_MIGRACOES,
            "--synapse.datasource.general.url=" + url,
            "--synapse.datasource.general.username=" + usuario,
            "--synapse.datasource.general.password=" + senha,
            "--synapse.datasource.general.hikari.maximum-pool-size=2",
            "--synapse.datasource.general.hikari.minimum-idle=0",
            "--synapse.datasource.chat.url=" + url,
            "--synapse.datasource.chat.username=" + usuario,
            "--synapse.datasource.chat.password=" + senha,
            "--synapse.datasource.chat.hikari.maximum-pool-size=2",
            "--synapse.datasource.chat.hikari.minimum-idle=0"
        };
    }

    private static void inserirParSinteticoDeLeads() throws Exception {
        try (Connection conexao = DATA_SOURCE.getConnection(); Statement comando = conexao.createStatement()) {
            comando.executeUpdate(
                    "INSERT INTO lead (id, nome, telefone) VALUES "
                            + "('00000000-0000-4000-8000-000000000071', 'importado sintetico', '061999999999'), "
                            + "('00000000-0000-4000-8000-000000000072', 'contato sintetico', '5561999999999')");
            comando.executeUpdate(
                    "INSERT INTO atendimento (id, lead_id) VALUES "
                            + "('00000000-0000-4000-8000-000000000073', "
                            + "'00000000-0000-4000-8000-000000000072')");
        }
    }

    private static String versaoAtual(Flyway flyway) {
        var atual = flyway.info().current();
        return atual == null || atual.getVersion() == null ? "vazio" : atual.getVersion().getVersion();
    }

    private static int checksumV73() {
        try (Connection conexao = DATA_SOURCE.getConnection();
                var consulta = conexao.prepareStatement("SELECT checksum FROM flyway_schema_history WHERE version = '73'");
                var resultado = consulta.executeQuery()) {
            if (!resultado.next()) {
                throw new IllegalStateException("V73 não consta no histórico de teste");
            }
            return resultado.getInt(1);
        } catch (Exception erro) {
            throw new IllegalStateException("Falha ao consultar checksum de teste", erro);
        }
    }

    private static int contarV73() {
        try (Connection conexao = DATA_SOURCE.getConnection();
                var consulta = conexao.prepareStatement("SELECT count(*) FROM flyway_schema_history WHERE version = '73'");
                var resultado = consulta.executeQuery()) {
            resultado.next();
            return resultado.getInt(1);
        } catch (Exception erro) {
            throw new IllegalStateException("Falha ao contar V73 no histórico de teste", erro);
        }
    }

    private static boolean leadExiste(String id) {
        try (Connection conexao = DATA_SOURCE.getConnection();
                var consulta = conexao.prepareStatement("SELECT EXISTS (SELECT 1 FROM lead WHERE id = ?::uuid)")) {
            consulta.setString(1, id);
            try (var resultado = consulta.executeQuery()) {
                resultado.next();
                return resultado.getBoolean(1);
            }
        } catch (Exception erro) {
            throw new IllegalStateException("Falha ao consultar lead sintético", erro);
        }
    }

    private static String telefoneDoLead(String id) {
        try (Connection conexao = DATA_SOURCE.getConnection();
                var consulta = conexao.prepareStatement("SELECT telefone FROM lead WHERE id = ?::uuid")) {
            consulta.setString(1, id);
            try (var resultado = consulta.executeQuery()) {
                if (!resultado.next()) {
                    return null;
                }
                return resultado.getString(1);
            }
        } catch (Exception erro) {
            throw new IllegalStateException("Falha ao consultar telefone sintético", erro);
        }
    }
}
