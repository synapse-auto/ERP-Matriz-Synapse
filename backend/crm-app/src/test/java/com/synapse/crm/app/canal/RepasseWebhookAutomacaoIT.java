package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDeRepasseWebhook;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-secret=segredo-repasse-teste",
            "synapse.canal.whatsapp.webhook-verify-token=verify-repasse-teste",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000",
            "synapse.canal.outbox.maximo-de-tentativas=3",
            "synapse.canal.outbox.backoff-inicial=0s"
        })
class RepasseWebhookAutomacaoIT extends PostgresIT {

    private static final String SEGREDO = "segredo-repasse-teste";
    private static final String PREFIXO = "E25-REPASSE-";
    private static final String TELEFONE = "5561987654321";
    private static final ServidorAutomacao SERVIDOR = ServidorAutomacao.iniciar();

    @DynamicPropertySource
    static void configurarDestino(DynamicPropertyRegistry propriedades) {
        propriedades.add("synapse.automacao.repasse-webhook.url", SERVIDOR::url);
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcessadorDeWebhookEntrada processador;

    @Autowired
    private PublicadorDeRepasseWebhook publicador;

    private UUID leadId;

    @BeforeEach
    void preparar() {
        limpar();
        SERVIDOR.limpar();
        leadId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'IA')",
                leadId,
                PREFIXO + leadId,
                TELEFONE);
    }

    @AfterEach
    void limparDepois() {
        limpar();
    }

    @AfterAll
    static void pararServidor() {
        SERVIDOR.parar();
    }

    @Test
    @DisplayName("payload e assinatura chegam byte a byte iguais, somente depois do publisher")
    void payloadCru_eAssinatura_chegamSemAlteracaoEDeFormaAssincrona() {
        String payload = payload("wamid.E25-cru", "Olá, vidro 8 mm?\nSegunda linha");
        String assinatura = assinatura(payload);

        ResponseEntity<Void> resposta = postar(payload, assinatura);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(SERVIDOR.requisicoes()).isEmpty();
        assertThat(repassesPendentes()).isEqualTo(1);

        publicador.publicarPendentes();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(SERVIDOR.requisicoes()).hasSize(1);
            RequisicaoRecebida recebida = SERVIDOR.requisicoes().get(0);
            assertThat(recebida.corpo()).containsExactly(payload.getBytes(StandardCharsets.UTF_8));
            assertThat(recebida.assinatura()).isEqualTo(assinatura);
            assertThat(repassesPendentes()).isZero();
        });
    }

    @Test
    @DisplayName("Automacao fora do ar nao impede a mensagem de aparecer e o repasse fica para retry")
    void automacaoFora_naoAfetaEntradaDaMensagem() {
        SERVIDOR.responderCom(503);
        String payload = payload("wamid.E25-fora", "mensagem continua entrando");

        assertThat(postar(payload, assinatura(payload)).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(mensagensDoLead()).isEqualTo(1));

        publicador.publicarPendentes();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(mensagensDoLead()).isEqualTo(1);
            assertThat(repassesPendentes()).isEqualTo(1);
            assertThat(tentativasDoRepasse()).isEqualTo(1);
        });
    }

    private ResponseEntity<Void> postar(String payload, String assinatura) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", assinatura);
        return http.postForEntity(
                "/webhook/canal", new HttpEntity<>(payload, cabecalhos), Void.class);
    }

    private static String payload(String id, String texto) {
        return """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{"metadata":{"phone_number_id":"999999999999999"},"contacts":[{"profile":{"name":"Cliente E25"},"wa_id":"%s"}],"messages":[{"from":"%s","id":"%s","timestamp":"1786842000","text":{"body":"%s"},"type":"text"}]}}]}]}
                """
                .formatted(TELEFONE, TELEFONE, id, texto.replace("\n", "\\n"))
                .stripTrailing();
    }

    private static String assinatura(String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int mensagensDoLead() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id=m.atendimento_id WHERE a.lead_id=?",
                Integer.class,
                leadId);
    }

    private int repassesPendentes() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo='automacao.webhook.repassar' AND publicado_em IS NULL AND esgotado_em IS NULL",
                Integer.class);
    }

    private int tentativasDoRepasse() {
        return jdbc.queryForObject(
                "SELECT coalesce(max(tentativas), 0) FROM outbox_evento WHERE tipo='automacao.webhook.repassar'",
                Integer.class);
    }

    private void limpar() {
        jdbc.update("DELETE FROM outbox_evento WHERE tipo='automacao.webhook.repassar'");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE 'wamid.E25-%'");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    private record RequisicaoRecebida(byte[] corpo, String assinatura) {}

    private static final class ServidorAutomacao {
        private final HttpServer servidor;
        private final CopyOnWriteArrayList<RequisicaoRecebida> requisicoes =
                new CopyOnWriteArrayList<>();
        private final AtomicInteger status = new AtomicInteger(204);

        private ServidorAutomacao(HttpServer servidor) {
            this.servidor = servidor;
        }

        static ServidorAutomacao iniciar() {
            try {
                HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                ServidorAutomacao automacao = new ServidorAutomacao(servidor);
                servidor.createContext("/webhook/eventos", automacao::receber);
                servidor.start();
                return automacao;
            } catch (IOException e) {
                throw new IllegalStateException("nao foi possivel iniciar servidor de teste", e);
            }
        }

        String url() {
            return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/webhook/eventos";
        }

        void receber(HttpExchange troca) throws IOException {
            requisicoes.add(new RequisicaoRecebida(
                    troca.getRequestBody().readAllBytes(),
                    troca.getRequestHeaders().getFirst("X-Hub-Signature-256")));
            troca.sendResponseHeaders(status.get(), -1);
            troca.close();
        }

        void responderCom(int novoStatus) {
            status.set(novoStatus);
        }

        CopyOnWriteArrayList<RequisicaoRecebida> requisicoes() {
            return requisicoes;
        }

        void limpar() {
            requisicoes.clear();
            status.set(204);
        }

        void parar() {
            servidor.stop(0);
        }
    }
}
