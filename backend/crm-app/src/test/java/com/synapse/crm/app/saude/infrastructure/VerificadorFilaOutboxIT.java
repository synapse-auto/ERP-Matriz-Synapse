package com.synapse.crm.app.saude.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.saude.application.ComponenteDaSaude;
import com.synapse.crm.app.saude.application.StatusDoComponente;
import com.synapse.crm.atendimento.infrastructure.outbox.SaudeDoConsumidorDaOutbox;

/**
 * A checagem de acesso à fila deixou de ser {@code count(*)} (docs/49). Estes casos garantem que a
 * troca não virou um falso UP: fila inacessível e consumidor parado continuam DOWN.
 */
class VerificadorFilaOutboxIT extends PostgresIT {

    private static final SaudeCriticaProperties PROPRIEDADES =
            new SaudeCriticaProperties(Duration.ofMinutes(5), Duration.ofMinutes(5), 100, 3);
    /** Relógio em que o heartbeat inicial (EPOCH) ainda está dentro do limite: consumidor saudável. */
    private static final Clock LOGO_APOS_O_HEARTBEAT = Clock.fixed(Instant.EPOCH.plusSeconds(60), ZoneOffset.UTC);

    @Test
    @DisplayName("fila acessível e consumidor dentro do limite: UP")
    void filaAcessivel_consumidorRecente_up() {
        ComponenteDaSaude resultado = verificador(POSTGRES.getJdbcUrl(), LOGO_APOS_O_HEARTBEAT).verificar();

        assertThat(resultado.status()).isEqualTo(StatusDoComponente.UP);
    }

    @Test
    @DisplayName("tabela da fila inacessível: DOWN, mesmo com o consumidor saudável")
    void tabelaDaFilaInacessivel_down() {
        // Esquema sem outbox_evento no search_path: a relação não resolve, como tabela removida.
        String semFila = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=pg_catalog";

        ComponenteDaSaude resultado = verificador(semFila, LOGO_APOS_O_HEARTBEAT).verificar();

        assertThat(resultado.status()).isEqualTo(StatusDoComponente.DOWN);
    }

    @Test
    @DisplayName("banco inalcançável: DOWN")
    void bancoInalcancavel_down() {
        ComponenteDaSaude resultado =
                verificador("jdbc:postgresql://127.0.0.1:1/inexistente?connectTimeout=2", LOGO_APOS_O_HEARTBEAT)
                        .verificar();

        assertThat(resultado.status()).isEqualTo(StatusDoComponente.DOWN);
    }

    @Test
    @DisplayName("fila acessível com consumidor parado: DOWN — acesso barato não mascara consumo parado")
    void filaAcessivel_consumidorParado_down() {
        ComponenteDaSaude resultado = verificador(POSTGRES.getJdbcUrl(), Clock.systemUTC()).verificar();

        assertThat(resultado.status()).isEqualTo(StatusDoComponente.DOWN);
        assertThat(resultado.detalhe()).contains("consumidor");
    }

    private static VerificadorFilaOutbox verificador(String url, Clock relogio) {
        DataSource fonte = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        return new VerificadorFilaOutbox(new SaudeDoConsumidorDaOutbox(relogio), fonte, PROPRIEDADES, relogio);
    }
}
