package com.synapse.crm.app.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * E223 (PR B): carencia de partida. O backend acabou de subir (carencia de uma hora) e o registro de sessoes esta
 * vazio ate os clientes voltarem: ninguem pode ser marcado OFFLINE, senao cada deploy esvaziaria o rodizio.
 */
@TestPropertySource(
        properties = {
            "synapse.tempo-real.presenca.tolerancia=PT1S",
            "synapse.tempo-real.presenca.carencia=PT1H",
            "synapse.tempo-real.presenca.intervalo-varredura=PT0.001S"
        })
class PresencaAutomaticaCarenciaIT extends PresencaAutomaticaITBase {

    @Test
    void reinicioDoBackendDentroDaCarenciaNaoGeraOfflineMesmoPassadaATolerancia() {
        chave(true);
        presenca("ONLINE"); // estava online antes do deploy e o cliente ainda nao reconectou

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            automatica.varrer();
            assertThat(presenca()).isEqualTo("ONLINE");
        });
        assertThat(historico()).isEmpty();
    }

    @Test
    void naCarenciaSoOfflineVirOnlineAoConectarEOAusenteEscolhidoAntesDoDeployEPreservado() throws Exception {
        chave(true);

        presenca("AUSENTE");
        conectarComoAna();
        esperarSessoesDaAna(1);
        automatica.varrer();
        assertThat(presenca()).isEqualTo("AUSENTE");
        assertThat(historico()).isEmpty();
    }

    @Test
    void naCarenciaQuemEstavaOfflineVirOnlineAoConectar() throws Exception {
        chave(true);
        presenca("OFFLINE");

        conectarComoAna();

        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));
        assertThat(historico()).singleElement().satisfies(linha -> assertThat(linha)
                .containsEntry("origem", "SISTEMA")
                .containsEntry("motivo", "CONEXAO"));
    }
}
