package com.synapse.migrationrunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.resource.StringResource;
import org.junit.jupiter.api.Test;

class V73BatchMigrationTest {

    @Test
    void checksumDaImplementacaoPaginadaECalculadoDoSqlImutavel() {
        assertThat(V73__NormalizarPrefixoDiscagemLeads.checksumOriginal()).isNotNull();
        assertThat(new V73__NormalizarPrefixoDiscagemLeads("55", 25, 3, Duration.ofMinutes(15)).getChecksum())
                .isEqualTo(V73__NormalizarPrefixoDiscagemLeads.checksumOriginal());
    }

    @Test
    void providerDoRunnerNaoExponeOSqlMonolitico() {
        LoadableResource v72 = new StringResource("v72");
        LoadableResource v73 = new StringResource("v73") {
            @Override
            public String getFilename() {
                return "V73__normalizar_prefixo_discagem_leads.sql";
            }
        };
        ResourceProvider delegado = new ResourceProvider() {
            @Override
            public LoadableResource getResource(String nome) {
                return v73;
            }

            @Override
            public java.util.Collection<LoadableResource> getResources(String local, String[] sufixos) {
                return List.of(v72, v73);
            }
        };

        V73ResourceProvider provider = new V73ResourceProvider(delegado);

        assertThat(provider.getResource("db/migration/V73__normalizar_prefixo_discagem_leads.sql"))
                .isNull();
        assertThat(provider.getResources("db/migration", new String[] {".sql"}))
                .containsExactly(v72);
    }

    @Test
    void parametrosDoRunnerRecusamLoteOuLeaseInvalidos() {
        assertThatThrownBy(() -> new MigrationRunnerProperties(
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0, 3, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MigrationRunnerProperties(
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 25, 3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
