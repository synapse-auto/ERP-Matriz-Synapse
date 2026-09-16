package com.synapse.migrationrunner;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limites finitos de lock e execução usados somente pelo runner de migration. */
@ConfigurationProperties(prefix = "synapse.migration-runner")
public record MigrationRunnerProperties(Duration lockTimeout, Duration statementTimeout) {

    public MigrationRunnerProperties {
        validarDuracao("lockTimeout", lockTimeout);
        validarDuracao("statementTimeout", statementTimeout);
    }

    private static void validarDuracao(String nome, Duration valor) {
        Objects.requireNonNull(valor, nome + " deve ser configurado");
        if (valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException(nome + " deve ser um limite finito e positivo");
        }
        try {
            valor.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(nome + " excede o limite suportado", overflow);
        }
    }
}
