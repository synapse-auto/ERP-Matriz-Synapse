package com.synapse.migrationrunner;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Limites finitos de lock e execução usados somente pelo runner de migration. */
@ConfigurationProperties(prefix = "synapse.migration-runner")
public record MigrationRunnerProperties(
        Duration lockTimeout,
        Duration statementTimeout,
        Duration totalTimeout,
        int batchSize,
        int maxBatchAttempts,
        Duration batchLease) {

    private static final Duration DEFAULT_TOTAL_TIMEOUT = Duration.ofMinutes(45);

    public MigrationRunnerProperties(Duration lockTimeout, Duration statementTimeout) {
        this(lockTimeout, statementTimeout, DEFAULT_TOTAL_TIMEOUT, 25, 3, Duration.ofMinutes(15));
    }

    public MigrationRunnerProperties(Duration lockTimeout, Duration statementTimeout, Duration totalTimeout) {
        this(lockTimeout, statementTimeout, totalTimeout, 25, 3, Duration.ofMinutes(15));
    }

    @ConstructorBinding
    public MigrationRunnerProperties {
        validarDuracao("lockTimeout", lockTimeout);
        validarDuracao("statementTimeout", statementTimeout);
        validarDuracao("totalTimeout", totalTimeout);
        if (batchSize < 1 || maxBatchAttempts < 1) {
            throw new IllegalArgumentException("batchSize e maxBatchAttempts devem ser positivos");
        }
        validarDuracao("batchLease", batchLease);
    }

    private static void validarDuracao(String nome, Duration valor) {
        Objects.requireNonNull(valor, nome + " deve ser configurado");
        if (valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException(nome + " deve ser um limite finito e positivo");
        }
        try {
            if (valor.toMillis() <= 0) {
                throw new IllegalArgumentException(nome + " deve ser maior que um milissegundo");
            }
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(nome + " excede o limite suportado", overflow);
        }
    }
}
