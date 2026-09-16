package com.synapse.migrationrunner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;

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

    private final Flyway flyway;
    private final DataSource dataSource;

    public FlywayMigrationRunner(Flyway flyway, DataSource dataSource) {
        this.flyway = flyway;
        this.dataSource = dataSource;
    }

    public Resultado executar() {
        long inicioNanos = System.nanoTime();
        try (Connection conexaoLock = dataSource.getConnection()) {
            conexaoLock.setAutoCommit(true);
            if (!tentarAdquirirLock(conexaoLock)) {
                log.warn("[FLYWAY_CONTROLADO] execução recusada: já existe outra execução exclusiva");
                throw new MigracaoJaEmExecucaoException();
            }
            try {
                return executarComLock(inicioNanos);
            } finally {
                liberarLock(conexaoLock);
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

    private Resultado executarComLock(long inicioNanos) {
        flyway.validate();
        MigrationInfo atual = flyway.info().current();
        MigrationInfo[] pendentes = flyway.info().pending();
        String versaoAtual = atual == null || atual.getVersion() == null
                ? "vazio"
                : atual.getVersion().getVersion();

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
                "[FLYWAY_CONTROLADO] inicio: schemaAtual={} migrationsPendentes={} versoes={}",
                versaoAtual,
                pendentes.length,
                versoesPendentes.isBlank() ? "repetiveis" : versoesPendentes);

        try {
            MigrateResult resultado = flyway.migrate();
            flyway.validate();
            int restantes = flyway.info().pending().length;
            if (restantes != 0) {
                throw new IllegalStateException("Flyway encerrou com migrations ainda pendentes");
            }
            MigrationInfo finalizada = flyway.info().current();
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

    private static boolean tentarAdquirirLock(Connection conexao) throws SQLException {
        try (PreparedStatement consulta = conexao.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            consulta.setInt(1, LOCK_NAMESPACE);
            consulta.setInt(2, LOCK_RESOURCE);
            try (ResultSet resultado = consulta.executeQuery()) {
                return resultado.next() && resultado.getBoolean(1);
            }
        }
    }

    private static void liberarLock(Connection conexao) throws SQLException {
        try (PreparedStatement consulta = conexao.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            consulta.setInt(1, LOCK_NAMESPACE);
            consulta.setInt(2, LOCK_RESOURCE);
            try (ResultSet resultado = consulta.executeQuery()) {
                if (!resultado.next() || !resultado.getBoolean(1)) {
                    throw new IllegalStateException("Advisory lock da migration não foi liberado");
                }
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
}
