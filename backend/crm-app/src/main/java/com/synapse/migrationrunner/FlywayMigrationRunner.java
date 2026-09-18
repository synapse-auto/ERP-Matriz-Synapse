package com.synapse.migrationrunner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executa migrations explicitamente sob um advisory lock de sessão, sem aguardar concorrentes. */
public class FlywayMigrationRunner {

    static final int LOCK_NAMESPACE = 0x53594E; // "SYN" em ASCII; namespace da ferramenta Synapse.
    static final int LOCK_RESOURCE = 73;
    static final String VERSAO_ORIGEM = "72";
    static final String VERSAO_ALVO = "73";

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);
    private static final Duration ENCERRAMENTO_WORKER = Duration.ofSeconds(5);

    private final Flyway flyway;
    private final DataSource dataSource;
    private final MigrationRunnerProperties propriedades;

    public FlywayMigrationRunner(Flyway flyway, DataSource dataSource) {
        this(flyway, dataSource, new MigrationRunnerProperties(Duration.ofSeconds(10), Duration.ofMinutes(30)));
    }

    public FlywayMigrationRunner(
            Flyway flyway, DataSource dataSource, MigrationRunnerProperties propriedades) {
        this.flyway = flyway;
        this.dataSource = dataSource;
        this.propriedades = propriedades;
    }

    public Resultado executar() {
        long inicioNanos = System.nanoTime();
        var conexoes = new ConnectionTrackingDataSource(dataSource);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "synapse-v73-migration-runner");
            thread.setDaemon(true);
            return thread;
        });
        Future<Resultado> futuro = executor.submit(() -> executarComTimeout(conexoes, inicioNanos));
        log.info("[FLYWAY_CONTROLADO] runner iniciado: timeoutTotalMs={}", propriedades.totalTimeout().toMillis());
        try {
            return futuro.get(propriedades.totalTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException erro) {
            log.error(
                    "[FLYWAY_CONTROLADO] timeout total após {} ms; conexoesAtivas={}; execução abortada",
                    elapsedMs(inicioNanos),
                    conexoes.conexoesAtivas());
            futuro.cancel(true);
            conexoes.fecharConexoesAtivas();
            throw new MigrationRunnerTimeoutException();
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            futuro.cancel(true);
            conexoes.fecharConexoesAtivas();
            throw new MigrationRunnerInterruptedException();
        } catch (ExecutionException erro) {
            Throwable causa = erro.getCause();
            if (causa instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Runner de migration falhou", causa);
        } finally {
            conexoes.fecharConexoesAtivas();
            executor.shutdownNow();
            aguardarEncerramento(executor);
            fecharPoolDoRunner();
        }
    }

    private Resultado executarComTimeout(ConnectionTrackingDataSource conexoes, long inicioNanos) {
        Flyway flywayControlado = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .dataSource(conexoes)
                .load();
        try (Connection conexaoLock = conexoes.getConnection()) {
            conexaoLock.setAutoCommit(true);
            if (!tentarAdquirirLock(conexaoLock)) {
                log.warn("[FLYWAY_CONTROLADO] execução recusada: já existe outra execução exclusiva");
                throw new MigracaoJaEmExecucaoException();
            }
            log.info("[FLYWAY_CONTROLADO] advisory lock adquirido");
            try {
                return executarComLock(flywayControlado, conexoes, inicioNanos);
            } finally {
                liberarLockSeguro(conexaoLock);
            }
        } catch (SQLException erro) {
            log.error(
                    "[FLYWAY_CONTROLADO] falha de conexão/lock após {} ms; detalhe SQL omitido",
                    elapsedMs(inicioNanos));
            throw new IllegalStateException("Runner de migration falhou ao acessar o banco", erro);
        } catch (RuntimeException erro) {
            if (!(erro instanceof MigracaoJaEmExecucaoException)) {
                log.error(
                        "[FLYWAY_CONTROLADO] falha após {} ms; tipo={} (detalhe omitido)",
                        elapsedMs(inicioNanos),
                        erro.getClass().getSimpleName());
            }
            throw erro;
        }
    }

    private Resultado executarComLock(
            Flyway flywayControlado, ConnectionTrackingDataSource conexoes, long inicioNanos) {
        log.info("[FLYWAY_CONTROLADO] validando histórico e checksums");
        validarHistorico(flywayControlado);
        MigrationInfo atual = flywayControlado.info().current();
        MigrationInfo[] pendentes = flywayControlado.info().pending();
        String versaoAtual = atual == null || atual.getVersion() == null
                ? "vazio"
                : atual.getVersion().getVersion();

        log.info(
                "[FLYWAY_CONTROLADO] migrations descobertas: schemaAtual={} pendentes={}",
                versaoAtual,
                pendentes.length);
        if (pendentes.length == 0 && VERSAO_ALVO.equals(versaoAtual)) {
            log.info(
                    "[FLYWAY_CONTROLADO] sem trabalho: schemaAtual={} pendentes=0 duracaoMs={}",
                    versaoAtual,
                    elapsedMs(inicioNanos));
            return new Resultado(versaoAtual, 0, false, elapsedMs(inicioNanos));
        }

        boolean estado72ComApenasV73 = VERSAO_ORIGEM.equals(versaoAtual)
                && pendentes.length == 1
                && pendentes[0].getVersion() != null
                && VERSAO_ALVO.equals(pendentes[0].getVersion().getVersion());
        if (!estado72ComApenasV73) {
            throw new IllegalStateException(
                    "Estado do schema não suportado pelo runner exclusivo da V73; nenhuma migration foi iniciada");
        }

        String versoesPendentes = Arrays.stream(pendentes)
                .map(MigrationInfo::getVersion)
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        log.info(
                "[FLYWAY_CONTROLADO] início da migration: schemaAtual={} migrationsPendentes={} versoes={}",
                versaoAtual,
                pendentes.length,
                versoesPendentes.isBlank() ? "repetiveis" : versoesPendentes);

        try {
            executarV73EmLotes(flywayControlado, conexoes);
            // A V73 SQL permanece a fonte imutável do histórico. Depois que a operação em lotes
            // terminou, o Flyway registra a mesma migration sem executar novamente o SQL pesado.
            Flyway registrarHistorico = Flyway.configure()
                    .configuration(flywayControlado.getConfiguration())
                    .dataSource(conexoes)
                    .skipExecutingMigrations(true)
                    .load();
            MigrateResult resultado = registrarHistorico.migrate();
            validarHistorico(flywayControlado);
            int restantes = flywayControlado.info().pending().length;
            if (restantes != 0) {
                throw new IllegalStateException("Flyway encerrou com migrations ainda pendentes");
            }
            MigrationInfo finalizada = flywayControlado.info().current();
            String versaoFinal = finalizada == null || finalizada.getVersion() == null
                    ? "vazio"
                    : finalizada.getVersion().getVersion();
            long duracaoMs = elapsedMs(inicioNanos);
            log.info(
                    "[FLYWAY_CONTROLADO] sucesso: schemaFinal={} migrationsExecutadas={} duracaoMs={}",
                    versaoFinal,
                    resultado.migrationsExecuted,
                    duracaoMs);
            return new Resultado(versaoFinal, resultado.migrationsExecuted, true, duracaoMs);
        } catch (RuntimeException erro) {
            // Não propaga exceções SQL detalhadas: a V73 pode incluí-las em erros de dados.
            throw new IllegalStateException("Migration controlada falhou; consultar estado e logs seguros");
        }
    }

    private void executarV73EmLotes(Flyway flywayControlado, ConnectionTrackingDataSource conexoes) {
        String ddiPadrao = flywayControlado.getConfiguration().getPlaceholders().getOrDefault("telefone_ddi_padrao", "55");
        V73__NormalizarPrefixoDiscagemLeads migration = new V73__NormalizarPrefixoDiscagemLeads(
                ddiPadrao, propriedades.batchSize(), propriedades.maxBatchAttempts(), propriedades.batchLease());
        try (Connection conexao = conexoes.getConnection()) {
            conexao.setAutoCommit(true);
            migration.migrate(new ContextoMigration(flywayControlado.getConfiguration(), conexao));
        } catch (Exception erro) {
            throw new IllegalStateException("Migration controlada falhou; consultar estado e logs seguros", erro);
        }
    }

    private record ContextoMigration(
            org.flywaydb.core.api.configuration.Configuration configuration, Connection connection)
            implements org.flywaydb.core.api.migration.Context {
        @Override
        public org.flywaydb.core.api.configuration.Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }
    }

    private static void validarHistorico(Flyway flywayControlado) {
        try {
            flywayControlado.validate();
        } catch (RuntimeException erro) {
            // O detalhe da validação pode carregar nomes de scripts e dados do ambiente.
            throw new IllegalStateException("Falha ao validar histórico/checksum; consultar estado operacional");
        }
    }

    private static boolean tentarAdquirirLock(Connection conexao) throws SQLException {
        try (PreparedStatement consulta = conexao.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            consulta.setInt(1, LOCK_NAMESPACE);
            consulta.setInt(2, LOCK_RESOURCE);
            try (ResultSet resultado = consulta.executeQuery()) {
                return resultado.next() && resultado.getBoolean(1);
            }
        }
    }

    private static void liberarLockSeguro(Connection conexao) {
        try (PreparedStatement consulta = conexao.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            consulta.setInt(1, LOCK_NAMESPACE);
            consulta.setInt(2, LOCK_RESOURCE);
            try (ResultSet resultado = consulta.executeQuery()) {
                if (!resultado.next() || !resultado.getBoolean(1)) {
                    throw new IllegalStateException("Advisory lock da migration não foi liberado");
                }
                log.info("[FLYWAY_CONTROLADO] advisory lock liberado");
            }
        } catch (SQLException erro) {
            if (conexaoFoiEncerrada(conexao)) {
                log.warn("[FLYWAY_CONTROLADO] conexão encerrada durante cancelamento; lock liberado pelo PostgreSQL");
                return;
            }
            throw new IllegalStateException("Falha ao liberar advisory lock; consultar estado operacional", erro);
        }
    }

    private static boolean conexaoFoiEncerrada(Connection conexao) {
        try {
            return conexao.isClosed();
        } catch (SQLException ignorado) {
            return true;
        }
    }

    private void aguardarEncerramento(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(ENCERRAMENTO_WORKER.toMillis(), TimeUnit.MILLISECONDS)) {
                log.error("[FLYWAY_CONTROLADO] worker não encerrou no prazo; processo one-shot será finalizado");
            }
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            log.warn("[FLYWAY_CONTROLADO] interrupção ao aguardar encerramento do worker");
        }
    }

    private void fecharPoolDoRunner() {
        if (dataSource instanceof AutoCloseable recurso) {
            try {
                recurso.close();
            } catch (Exception erro) {
                log.warn(
                        "[FLYWAY_CONTROLADO] pool do runner não pôde ser fechado; tipo={}",
                        dataSource.getClass().getSimpleName());
            }
        }
    }

    private static long elapsedMs(long inicioNanos) {
        return Duration.ofNanos(System.nanoTime() - inicioNanos).toMillis();
    }

    public record Resultado(String versaoFinal, int migrationsExecutadas, boolean executou, long duracaoMs) {}

    public static final class MigracaoJaEmExecucaoException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public MigracaoJaEmExecucaoException() {
            super("Já existe outra execução exclusiva de migrations neste banco");
        }
    }

    public static final class MigrationRunnerTimeoutException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public MigrationRunnerTimeoutException() {
            super("Runner de migration excedeu o timeout total; estado do banco deve ser verificado");
        }
    }

    public static final class MigrationRunnerInterruptedException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public MigrationRunnerInterruptedException() {
            super("Runner de migration foi interrompido; estado do banco deve ser verificado");
        }
    }
}
