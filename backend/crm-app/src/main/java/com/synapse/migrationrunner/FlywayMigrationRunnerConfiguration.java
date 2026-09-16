package com.synapse.migrationrunner;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.synapse.crm.app.config.DataSourceConfig;

/** Beans do processo one-shot; esta configuração não é descoberta pelo component-scan do CRM. */
@Configuration(proxyBeanMethods = false)
public class FlywayMigrationRunnerConfiguration {

    @Bean
    FlywayMigrationStrategy naoMigrarAntesDoLock() {
        // O initializer automático do Boot roda antes do ApplicationRunner. A migration só pode
        // acontecer depois que FlywayMigrationRunner adquirir o advisory lock exclusivo.
        return flyway -> {};
    }

    @Bean
    FlywayConfigurationCustomizer limitesDoRunner(MigrationRunnerProperties propriedades) {
        return configuracao -> configuracao
                .locations("classpath:db/migration")
                .target(FlywayMigrationRunner.VERSAO_ALVO)
                // A validação permite pendentes; o runner valida a lista e a versão atual antes do migrate.
                .ignoreMigrationPatterns("*:pending", "*:future")
                .lockRetryCount(0)
                .initSql(montarInitSql(propriedades));
    }

    @Bean
    FlywayMigrationRunner flywayMigrationRunner(
            Flyway flyway, @Qualifier(DataSourceConfig.GENERAL_DATA_SOURCE) DataSource dataSource) {
        return new FlywayMigrationRunner(flyway, dataSource);
    }

    @Bean
    ApplicationRunner executarUmaVez(FlywayMigrationRunner runner) {
        return argumentos -> runner.executar();
    }

    static String montarInitSql(MigrationRunnerProperties propriedades) {
        long timeoutLockMs = propriedades.lockTimeout().toMillis();
        long timeoutStatementMs = propriedades.statementTimeout().toMillis();
        // A V73 emite RAISE NOTICE contendo dados de lead. O runner suprime NOTICE e registra
        // somente estado, versões, duração e resultado; limites positivos evitam timeout infinito.
        return "SELECT set_config('client_min_messages', 'warning', false), "
                + "set_config('lock_timeout', '" + timeoutLockMs + "ms', false), "
                + "set_config('statement_timeout', '" + timeoutStatementMs + "ms', false)";
    }
}
