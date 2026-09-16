package com.synapse.crm.app.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

class FlywayValidacaoNoBootConfigTest {

    @Test
    @DisplayName("sem V73 pendente, o boot preserva o fluxo normal de migrations")
    void bootFlyway_semV73Pendente_executaMigrations() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService informacoes = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(informacoes);
        when(informacoes.pending()).thenReturn(new org.flywaydb.core.api.MigrationInfo[0]);
        FlywayMigrationStrategy estrategia = new FlywayValidacaoNoBootConfig().validarSchemaSemMigrarNoBoot();

        estrategia.migrate(flyway);

        verify(flyway).validate();
        verify(flyway).migrate();
    }

    @Test
    @DisplayName("com V73 pendente no schema72, o boot não executa nenhuma migration")
    void bootFlyway_comV73Pendente_paraAntesDoBackfill() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService informacoes = mock(MigrationInfoService.class);
        MigrationInfo v73 = mock(MigrationInfo.class);
        MigrationInfo atual = mock(MigrationInfo.class);
        when(flyway.info()).thenReturn(informacoes);
        when(informacoes.pending()).thenReturn(new MigrationInfo[] {v73});
        when(informacoes.current()).thenReturn(atual);
        when(v73.getVersion()).thenReturn(MigrationVersion.fromVersion("73"));
        when(atual.getVersion()).thenReturn(MigrationVersion.fromVersion("72"));
        FlywayMigrationStrategy estrategia = new FlywayValidacaoNoBootConfig().validarSchemaSemMigrarNoBoot();

        estrategia.migrate(flyway);

        verify(flyway).validate();
        verify(flyway, never()).migrate();
    }
}
