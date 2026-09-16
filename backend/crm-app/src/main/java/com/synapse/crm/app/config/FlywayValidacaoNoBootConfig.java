package com.synapse.crm.app.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Impede somente a execução da V73 pesada no processo normal do CRM.
 *
 * <p>Se a V73 estiver pendente, o boot pode preparar um schema novo/antigo até V72, mas para antes
 * da migration de dados. Assim a V73 nunca segura o boot normal. Quando ela não está pendente, o
 * fluxo Flyway normal continua aplicando migrations posteriores (por exemplo, V74).
 */
@Configuration(proxyBeanMethods = false)
public class FlywayValidacaoNoBootConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayValidacaoNoBootConfig.class);
    private static final MigrationVersion VERSAO_PESADA = MigrationVersion.fromVersion("73");
    private static final MigrationVersion VERSAO_ANTERIOR = MigrationVersion.fromVersion("72");

    @Bean
    public FlywayMigrationStrategy validarSchemaSemMigrarNoBoot() {
        return flyway -> {
            flyway.validate();
            MigrationInfo[] pendentes = flyway.info().pending();
            boolean v73Pendente = java.util.Arrays.stream(pendentes)
                    .map(MigrationInfo::getVersion)
                    .anyMatch(VERSAO_PESADA::equals);
            if (!v73Pendente) {
                flyway.migrate();
                return;
            }

            MigrationInfo atual = flyway.info().current();
            if (atual == null || atual.getVersion() == null || atual.getVersion().compareTo(VERSAO_ANTERIOR) < 0) {
                // Banco novo ou anterior à V72: preserva o boot convencional até a fronteira segura.
                Flyway ateV72 = Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .target(VERSAO_ANTERIOR)
                        .load();
                ateV72.migrate();
                ateV72.validate();
                pendentes = flyway.info().pending();
            } else if (atual.getVersion().compareTo(VERSAO_ANTERIOR) > 0) {
                throw new IllegalStateException(
                        "V73 pendente com schema posterior a V72; boot recusado para evitar migration fora de ordem");
            }

            if (pendentes.length > 0) {
                String versoes = java.util.Arrays.stream(pendentes)
                        .map(MigrationInfo::getVersion)
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .distinct()
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
                log.warn(
                        "[FLYWAY_PENDENTE] Boot pausado antes da V73 pesada; {} migration(s) seguem pendentes "
                                + "(versões: {}). Execute o runner controlado da V73.",
                        pendentes.length,
                        versoes.isBlank() ? "repetíveis" : versoes);
            }
        };
    }
}
