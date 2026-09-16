package com.synapse.migrationrunner;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import com.synapse.crm.app.config.DataSourceConfig;

/**
 * Composição mínima para executar migrations sob demanda.
 *
 * <p>Fica fora do component-scan do CRM e importa apenas os pools do banco e o Flyway; não inicia
 * API, JPA, schedulers, consumidores nem listeners do caminho de mensagens.
 */
@SpringBootConfiguration
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@EnableConfigurationProperties(MigrationRunnerProperties.class)
@Import({DataSourceConfig.class, FlywayMigrationRunnerConfiguration.class})
public class FlywayMigrationRunnerApplication {}
