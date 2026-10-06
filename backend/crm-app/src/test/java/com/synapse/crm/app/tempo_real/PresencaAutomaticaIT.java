package com.synapse.crm.app.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.context.TestPropertySource;

/**
 * E223 (PR B): presenca automatica com sessoes STOMP reais. Tolerancia de 3 s e sem carencia de partida; a chave de
 * cache da configuracao e de 1 ms, entao ligar/desligar a chave vale no proximo evento.
 */
@TestPropertySource(
        properties = {
            "synapse.seguranca.token-interno=e223b-token",
            "synapse.tempo-real.presenca.tolerancia=PT3S",
            "synapse.tempo-real.presenca.carencia=PT0S",
            "synapse.tempo-real.presenca.intervalo-varredura=PT0.001S"
        })
class PresencaAutomaticaIT extends PresencaAutomaticaITBase {

    private static final Duration TOLERANCIA = Duration.ofSeconds(3);

    @Autowired private RedisConnectionFactory redis;
    @Autowired private StringRedisTemplate texto;

    @Test
    void conectarViraOnlineGravaHistoricoDoSistemaEMantemOEstadoDepois() throws Exception {
        chave(true);
        presenca("OFFLINE");

        conectarComoAna();

        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));
        assertThat(historico()).singleElement().satisfies(linha -> assertThat(linha)
                .containsEntry("estado_anterior", "OFFLINE")
                .containsEntry("estado_novo", "ONLINE")
                .containsEntry("origem", "SISTEMA")
                .containsEntry("motivo", "CONEXAO"));
    }

    @Test
    void aMudancaAutomaticaAvisaAFilaPessoalParaASidebarAtualizarSemF5() throws Exception {
        chave(true);
        presenca("OFFLINE");
        BlockingQueue<String> avisos = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer escuta = new RedisMessageListenerContainer();
        escuta.setConnectionFactory(redis);
        escuta.afterPropertiesSet();
        escuta.start();
        try {
            escuta.addMessageListener(
                    (mensagem, padrao) -> avisos.add(new String(mensagem.getBody(), StandardCharsets.UTF_8)),
                    new ChannelTopic("synapse:aviso-usuario"));
            // A assinatura e assincrona e o pub/sub nao reentrega: manda uma sonda ate o listener recebe-la, so
            // entao provoca a mudanca. A sonda tem um usuario aleatorio: o subscriber a entrega a ninguem.
            String sonda = "{\"usuarioId\":\"" + UUID.randomUUID() + "\",\"tipo\":\"SONDA\",\"eventoId\":\"e223\",\"dados\":{}}";
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                texto.convertAndSend("synapse:aviso-usuario", sonda);
                return !avisos.isEmpty();
            });
            avisos.clear();

            conectarComoAna();

            await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
                String bruto = avisos.poll(200, TimeUnit.MILLISECONDS);
                assertThat(bruto).isNotNull();
                JsonNode aviso = mapeador.readTree(bruto);
                assertThat(aviso.path("tipo").asText()).isEqualTo("PRESENCA_ALTERADA");
                assertThat(aviso.path("usuarioId").asText()).isEqualTo(ana.toString());
                assertThat(aviso.path("dados").path("status").asText()).isEqualTo("ONLINE");
            });
        } finally {
            escuta.stop();
            escuta.destroy();
        }
    }

    @Test
    void conectarTiraDoAusenteDeOntemSePassouDaCarencia() throws Exception {
        chave(true);
        presenca("AUSENTE");

        conectarComoAna();

        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));
        assertThat(historico()).singleElement().satisfies(linha -> assertThat(linha)
                .containsEntry("estado_anterior", "AUSENTE")
                .containsEntry("origem", "SISTEMA"));
    }

    @Test
    void segundaAbaNaoDuplicaNemMudaNada() throws Exception {
        chave(true);
        presenca("OFFLINE");
        conectarComoAna();
        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));

        conectarComoAna();
        esperarSessoesDaAna(2);

        assertThat(presenca()).isEqualTo("ONLINE");
        assertThat(historico()).hasSize(1);
    }

    @Test
    void fecharUmaAbaMantendoOutraContinuaOnlineMesmoDepoisDaTolerancia() throws Exception {
        chave(true);
        presenca("OFFLINE");
        StompSession primeira = conectarComoAna();
        conectarComoAna();
        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));

        fechar(primeira);
        esperarSessoesDaAna(1);

        // Durante mais que a tolerancia, a cada varredura, ela continua ONLINE.
        await().during(TOLERANCIA.plusSeconds(1)).atMost(TOLERANCIA.plusSeconds(4)).untilAsserted(() -> {
            automatica.varrer();
            assertThat(presenca()).isEqualTo("ONLINE");
        });
        assertThat(historico()).hasSize(1);
    }

    @Test
    void fecharTodasAsAbasSoViraOfflineDepoisDaTolerancia() throws Exception {
        chave(true);
        presenca("OFFLINE");
        StompSession sessao = conectarComoAna();
        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));

        fechar(sessao);
        esperarSessoesDaAna(0);
        automatica.varrer();
        assertThat(presenca()).as("logo depois de fechar ainda dentro da tolerancia").isEqualTo("ONLINE");

        await().atMost(TOLERANCIA.plusSeconds(8)).untilAsserted(() -> {
            automatica.varrer();
            assertThat(presenca()).isEqualTo("OFFLINE");
        });
        assertThat(historico()).hasSize(2);
        assertThat(historico().get(1))
                .containsEntry("estado_anterior", "ONLINE")
                .containsEntry("estado_novo", "OFFLINE")
                .containsEntry("origem", "SISTEMA")
                .containsEntry("motivo", "DESCONEXAO");
    }

    @Test
    void reconexaoDentroDaToleranciaNaoMudaNadaENaoGravaHistorico() throws Exception {
        chave(true);
        presenca("OFFLINE");
        StompSession sessao = conectarComoAna();
        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));
        assertThat(patchPresencaDaAna("AUSENTE").getStatusCode()).isEqualTo(HttpStatus.OK);
        int linhasAntes = historico().size();

        // O frontend troca o socket a cada renovacao do token: fecha e abre outro logo em seguida.
        fechar(sessao);
        esperarSessoesDaAna(0);
        conectarComoAna();
        esperarSessoesDaAna(1);

        await().during(TOLERANCIA.plusSeconds(1)).atMost(TOLERANCIA.plusSeconds(4)).untilAsserted(() -> {
            automatica.varrer();
            assertThat(presenca()).as("o AUSENTE escolhido por ela continua").isEqualTo("AUSENTE");
        });
        assertThat(historico()).hasSize(linhasAntes);
    }

    @Test
    void ausenteManualERespeitadoDuranteASessaoInclusiveComOutraAba() throws Exception {
        chave(true);
        presenca("OFFLINE");
        conectarComoAna();
        await().atMost(Duration.ofSeconds(5)).until(() -> "ONLINE".equals(presenca()));
        patchPresencaDaAna("AUSENTE");
        int linhasAntes = historico().size();

        conectarComoAna();
        esperarSessoesDaAna(2);
        automatica.varrer();

        assertThat(presenca()).isEqualTo("AUSENTE");
        assertThat(historico()).hasSize(linhasAntes);
    }

    @Test
    void chaveDesligadaFicaIgualAoPrAConectarEDesconectarNaoMudamNada() throws Exception {
        chave(false);
        presenca("OFFLINE");

        StompSession sessao = conectarComoAna();
        esperarSessoesDaAna(1);
        automatica.varrer();
        assertThat(presenca()).isEqualTo("OFFLINE");

        presenca("ONLINE");
        fechar(sessao);
        esperarSessoesDaAna(0);
        await().during(TOLERANCIA.plusSeconds(1)).atMost(TOLERANCIA.plusSeconds(4)).untilAsserted(() -> {
            automatica.varrer();
            assertThat(presenca()).isEqualTo("ONLINE");
        });
        assertThat(historico()).isEmpty();
    }

    @Test
    void oRodizioContinuaExigindoOnlineEntraAoConectarESaiDepoisDaTolerancia() throws Exception {
        chave(true);
        ligarParaIa();
        presenca("OFFLINE");
        assertThat(rodizio("e223b-token")).doesNotContain(ana);

        StompSession sessao = conectarComoAna();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(rodizio("e223b-token")).contains(ana));

        fechar(sessao);
        esperarSessoesDaAna(0);
        await().atMost(TOLERANCIA.plusSeconds(8)).untilAsserted(() -> {
            automatica.varrer();
            assertThat(rodizio("e223b-token")).doesNotContain(ana);
        });
    }
}
