package com.synapse.crm.app;

import java.util.Arrays;

import com.synapse.migrationrunner.FlywayMigrationRunnerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Ponto de entrada da instancia.
 *
 * <p>Este e o unico modulo do monorepo com {@code @SpringBootApplication}: os demais sao
 * bibliotecas. O scan aponta para {@code com.synapse.crm} inteiro porque os modulos vivem em
 * pacotes irmaos deste — a composicao acontece aqui, e nenhum modulo conhece a aplicacao.
 */
@SpringBootApplication(scanBasePackages = SynapseCrmApplication.PACOTE_RAIZ)
@ConfigurationPropertiesScan(SynapseCrmApplication.PACOTE_RAIZ)
@EntityScan(SynapseCrmApplication.PACOTE_RAIZ)
@EnableJpaRepositories(SynapseCrmApplication.PACOTE_RAIZ)
public class SynapseCrmApplication {

    static final String PACOTE_RAIZ = "com.synapse.crm";
    public static final String ARGUMENTO_RUNNER_MIGRACOES = "--synapse.migrations.run-once";

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(ARGUMENTO_RUNNER_MIGRACOES)) {
            String[] argumentosDoRunner = Arrays.stream(args)
                    .filter(argumento -> !ARGUMENTO_RUNNER_MIGRACOES.equals(argumento))
                    .toArray(String[]::new);
            var contexto = new SpringApplicationBuilder(FlywayMigrationRunnerApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(argumentosDoRunner);
            // O runner termina após a execução e fecha os pools sem iniciar o CRM.
            contexto.close();
            return;
        }
        SpringApplication.run(SynapseCrmApplication.class, args);
    }
}
