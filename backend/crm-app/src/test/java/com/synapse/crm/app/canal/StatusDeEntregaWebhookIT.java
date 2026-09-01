package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.application.AplicarStatusDeEntregaDoCanalUseCase;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusDeEntregaDoCanal;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-secret=" + StatusDeEntregaWebhookIT.APP_SECRET,
            "synapse.canal.whatsapp.webhook-verify-token=verify-e118",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class StatusDeEntregaWebhookIT extends PostgresIT {

    static final String APP_SECRET = "segredo-e118-status-de-entrega";
    private static final String CREDENCIAL_ID = "cc000000-0000-4000-8000-000000000001";
    private static final String PHONE_NUMBER_ID = "1307417749115229";
    private static final String PREFIXO_WAMID = "wamid.E118-";
    private static final String PREFIXO_TELEFONE = "55619987611";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcessadorDeWebhookEntrada processador;

    @Autowired
    private AplicarStatusDeEntregaDoCanalUseCase aplicarStatus;

    private UUID leadId;
    private UUID atendimentoId;
    private UUID mensagemId;
    private Instant enviadoEm;
    private String wamid;

    @BeforeEach
    void preparar() {
        limpar();
        jdbc.update(
                "UPDATE canal_credencial SET identificador_externo = ?, ativo = TRUE,"
                        + " vigente_ate = NULL WHERE id = ?::uuid",
                PHONE_NUMBER_ID,
                CREDENCIAL_ID);
        leadId = UUID.randomUUID();
        atendimentoId = UUID.randomUUID();
        mensagemId = UUID.randomUUID();
        enviadoEm = Instant.parse("2026-09-01T15:00:00Z");
        wamid = PREFIXO_WAMID + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                leadId,
                "Cliente E118",
                PREFIXO_TELEFONE + "01");
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em)"
                        + " VALUES (?, ?, 'EM_ATENDIMENTO', now())",
                atendimentoId,
                leadId);
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo,"
                        + " status_entrega, enviado_em)"
                        + " VALUES (?, ?, 'ATENDENTE', 'TEXTO', 'oi', 'ENVIADO', ?)",
                mensagemId,
                atendimentoId,
                Timestamp.from(enviadoEm));
        jdbc.update(
                "INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)"
                        + " VALUES (?, ?, ?, ?)",
                wamid,
                mensagemId,
                Timestamp.from(enviadoEm),
                atendimentoId);
    }

    @AfterEach
    void restaurar() {
        limpar();
        jdbc.update(
                "UPDATE canal_credencial SET identificador_externo = '999999999999999'"
                        + " WHERE id = ?::uuid",
                CREDENCIAL_ID);
    }

    @Test
    @DisplayName("sent, delivered e read movem a mensagem pelos tres estados")
    void sentDeliveredRead_avancaOsTresEstados() {
        assertThat(postar(payloadDeStatus(wamid, "sent")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("ENVIADO");

        assertThat(postar(payloadDeStatus(wamid, "delivered")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("ENTREGUE");

        assertThat(postar(payloadDeStatus(wamid, "read")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("LIDO");
    }

    @Test
    @DisplayName("read antes de delivered fica LIDO e o delivered atrasado nao rebaixa")
    void readAntesDeDelivered_naoRebaixa() {
        assertThat(postar(payloadDeStatus(wamid, "read")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("LIDO");

        assertThat(postar(payloadDeStatus(wamid, "delivered")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("LIDO");
    }

    @Test
    @DisplayName("o mesmo status duas vezes nao muda o estado")
    void mesmoStatusDuasVezes_idempotente() {
        assertThat(postar(payloadDeStatus(wamid, "delivered")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postar(payloadDeStatus(wamid, "delivered")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("ENTREGUE");
    }

    @Test
    @DisplayName("failed marca FALHOU e guarda codigo e titulo")
    void failed_marcaFalhouEPreservaErro() {
        String payload = payloadDeStatusComErro(wamid, 131053, "Media upload error");

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("FALHOU");
        assertThat(jdbc.queryForObject(
                        "SELECT erro_entrega->>'codigo' FROM mensagem WHERE id = ?",
                        String.class,
                        mensagemId))
                .isEqualTo("131053");
        assertThat(jdbc.queryForObject(
                        "SELECT erro_entrega->>'titulo' FROM mensagem WHERE id = ?",
                        String.class,
                        mensagemId))
                .isEqualTo("Media upload error");
    }

    @Test
    @DisplayName("wamid desconhecido responde 200, nao estoura e nao grava")
    void wamidDesconhecido_responde200(CapturedOutput log) {
        assertThat(postar(payloadDeStatus(PREFIXO_WAMID + "ghost", "delivered")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("ENVIADO");
        assertThat(log.getOut()).contains("wamid(s) desconhecido(s)");
    }

    @Test
    @DisplayName("payload so com statuses e processado — nao sai no idsExternos vazio")
    void payloadSoComStatuses_processa() {
        assertThat(postar(payloadDeStatus(wamid, "delivered")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("ENTREGUE");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM webhook_entrada WHERE id_externo LIKE ?",
                        Integer.class,
                        PREFIXO_WAMID + "%"))
                .isZero();
    }

    @Test
    @DisplayName("payload misto aplica o status e registra a mensagem nova")
    void payloadMisto_statusEMensagem() {
        String telefone = PREFIXO_TELEFONE + "02";
        String inboundId = PREFIXO_WAMID + "inbound";
        String payload = payload(
                mudancaMista(
                        PHONE_NUMBER_ID,
                        statusJson(wamid, "delivered"),
                        contatoJson(telefone)
                                + ",\"messages\":["
                                + mensagemJson(telefone, inboundId, "oi do cliente")
                                + "]"));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDaMensagem()).isEqualTo("ENTREGUE");

        processador.processarPendentes();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                                + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone = ?",
                        Integer.class,
                        telefone))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("sem contexto de servico a RLS impede a escrita; com contexto aplica")
    void semContextoDeServico_naoEscreve() {
        var pedido = List.of(new StatusDeEntregaDoCanal(wamid, "ENTREGUE", null, null));

        aplicarStatus.executar(pedido);
        assertThat(statusDaMensagem()).isEqualTo("ENVIADO");

        ContextoDeServico.executarComo("teste-e118", () -> aplicarStatus.executar(pedido));
        assertThat(statusDaMensagem()).isEqualTo("ENTREGUE");
    }

    private String statusDaMensagem() {
        return jdbc.queryForObject(
                "SELECT status_entrega::text FROM mensagem WHERE id = ?", String.class, mensagemId);
    }

    private org.springframework.http.ResponseEntity<Void> postar(String payload) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", assinatura(payload));
        return http.postForEntity(
                "/webhook/canal", new HttpEntity<>(payload, cabecalhos), Void.class);
    }

    private static String payloadDeStatus(String wamidAlvo, String statusMeta) {
        return payload(mudancaDeStatus(PHONE_NUMBER_ID, statusJson(wamidAlvo, statusMeta)));
    }

    private static String payloadDeStatusComErro(String wamidAlvo, int codigo, String titulo) {
        return payload(mudancaDeStatus(
                PHONE_NUMBER_ID,
                "{\"id\":\"%s\",\"status\":\"failed\",\"errors\":[{\"code\":%d,\"title\":\"%s\"}]}"
                        .formatted(wamidAlvo, codigo, titulo)));
    }

    private static String payload(String... mudancas) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":["
                + String.join(",", mudancas)
                + "]}]}";
    }

    private static String mudancaDeStatus(String destino, String status) {
        return """
                {"value":{"metadata":{"phone_number_id":"%s"},"statuses":[%s]}}
                """
                .formatted(destino, status)
                .strip();
    }

    private static String mudancaMista(String destino, String status, String restoDoValue) {
        return """
                {"value":{"metadata":{"phone_number_id":"%s"},"statuses":[%s],%s}}
                """
                .formatted(destino, status, restoDoValue)
                .strip();
    }

    private static String statusJson(String wamidAlvo, String statusMeta) {
        return "{\"id\":\"%s\",\"status\":\"%s\",\"timestamp\":\"1786842000\"}"
                .formatted(wamidAlvo, statusMeta);
    }

    private static String contatoJson(String telefone) {
        return "\"contacts\":[{\"wa_id\":\"%s\",\"profile\":{\"name\":\"Cliente E118 inbound\"}}]"
                .formatted(telefone);
    }

    private static String mensagemJson(String telefone, String id, String texto) {
        return "{\"from\":\"%s\",\"id\":\"%s\",\"timestamp\":\"1786842000\",\"type\":\"text\",\"text\":{\"body\":\"%s\"}}"
                .formatted(telefone, id, texto);
    }

    private static String assinatura(String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256="
                    + HexFormat.of().formatHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void limpar() {
        jdbc.update("DELETE FROM mensagem_id_externo WHERE wamid LIKE ?", PREFIXO_WAMID + "%");
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE ?", PREFIXO_WAMID + "%");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE ?", PREFIXO_WAMID + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone LIKE ?)"
                        + " OR id IN (SELECT mensagem_id FROM mensagem_id_externo WHERE wamid LIKE ?)",
                PREFIXO_TELEFONE + "%",
                PREFIXO_WAMID + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE telefone LIKE ?)",
                PREFIXO_TELEFONE + "%");
        jdbc.update("DELETE FROM lead WHERE telefone LIKE ?", PREFIXO_TELEFONE + "%");
        jdbc.update("DELETE FROM lead WHERE nome = 'Cliente E118'");
    }
}
