package com.synapse.crm.app.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * E193: o frame CONNECTED precisa anunciar heartbeat, e nao {@code 0,0}.
 *
 * <p>A tela de Atendimentos nao tem polling por decisao de projeto (docs/40): ela so atualiza
 * reagindo a um frame do WebSocket. Sem pulso, uma conexao que morre em silencio continua "aberta"
 * para os dois lados ate a pilha TCP desistir, sem prazo — o reconector nunca e chamado e a
 * mensagem ja recebida so aparece depois de cerca de um minuto, ou com F5. Este teste prova que o
 * broker anuncia os valores configurados; o tempo de deteccao em si e observacao operacional.
 *
 * <p>Os valores do teste sao diferentes do default de producao de proposito: fossem iguais, o teste
 * passaria mesmo se alguem fixasse o numero no codigo em vez de ler de {@code TempoRealProperties}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.tempo-real.outbox.intervalo-ms=3600000",
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.tempo-real.heartbeat-saida-ms=7000",
            "synapse.tempo-real.heartbeat-entrada-ms=9000",
            "synapse.tempo-real.reconexao-atraso-inicial-ms=1500",
            "synapse.tempo-real.reconexao-fator=2.5",
            "synapse.tempo-real.reconexao-atraso-maximo-ms=20000"
        })
class HeartbeatWebSocketIT extends PostgresIT {

    private static final long SAIDA_ESPERADA_MS = 7000L;
    private static final long ENTRADA_ESPERADA_MS = 9000L;

    @Autowired
    private TestRestTemplate http;

    private int porta;
    private WebSocketStompClient stomp;
    private ThreadPoolTaskScheduler agendadorDoCliente;
    private StompSession sessao;

    @Value("${local.server.port}")
    void definirPorta(int porta) {
        this.porta = porta;
    }

    @BeforeEach
    void preparar() {
        // O cliente tambem anuncia pulso, como o navegador faz via @stomp/stompjs: e assim que a
        // negociacao acontece de verdade.
        agendadorDoCliente = new ThreadPoolTaskScheduler();
        agendadorDoCliente.setPoolSize(1);
        agendadorDoCliente.setThreadNamePrefix("teste-heartbeat-");
        agendadorDoCliente.initialize();

        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setTaskScheduler(agendadorDoCliente);
        stomp.setDefaultHeartbeat(new long[] {10_000L, 10_000L});
    }

    @AfterEach
    void encerrar() {
        if (sessao != null && sessao.isConnected()) {
            sessao.disconnect();
        }
        stomp.stop();
        agendadorDoCliente.shutdown();
    }

    @Test
    @DisplayName("o frame CONNECTED anuncia heartbeat e backoff configurados")
    void connected_anunciaHeartbeatEBackoffConfigurados() throws Exception {
        String token = ApoioAutenticacao.login(
                        http, ApoioAutenticacao.EMAIL_ANA, ApoioAutenticacao.SENHA_ATENDENTE)
                .accessToken();
        CompletableFuture<StompHeaders> cabecalhosAnunciados = new CompletableFuture<>();

        sessao = stomp.connectAsync(
                        "ws://localhost:" + porta + "/ws?access_token=" + token,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void afterConnected(
                                    StompSession sessaoConectada, StompHeaders cabecalhosDoConnected) {
                                cabecalhosAnunciados.complete(cabecalhosDoConnected);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        StompHeaders cabecalhos = cabecalhosAnunciados.get(5, TimeUnit.SECONDS);
        long[] pulso = cabecalhos.getHeartbeat();

        assertThat(pulso)
                .as("CONNECTED sem heartbeat (0,0) e o que deixava a conexao morta passar despercebida")
                .isNotNull()
                .containsExactly(SAIDA_ESPERADA_MS, ENTRADA_ESPERADA_MS);
        assertThat(cabecalhos.getFirst("x-synapse-reconexao-atraso-inicial-ms")).isEqualTo("1500");
        assertThat(cabecalhos.getFirst("x-synapse-reconexao-fator")).isEqualTo("2.5");
        assertThat(cabecalhos.getFirst("x-synapse-reconexao-atraso-maximo-ms")).isEqualTo("20000");
    }
}
