package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

/**
 * E134 — resposta interativa do cliente (lista/botão) precisa virar mensagem LEAD no histórico.
 * A Automação já lia o payload cru; o tradutor descartava o título.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-secret=" + RespostaInterativaWebhookIT.APP_SECRET,
            "synapse.canal.whatsapp.webhook-verify-token=verify-e134",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class RespostaInterativaWebhookIT extends PostgresIT {

    static final String APP_SECRET = "segredo-e134-para-assinatura-do-webhook";
    private static final String PHONE_NUMBER_ID = "999999999999999";
    private static final String PREFIXO_ID = "wamid.E134-";
    private static final String TELEFONE = "5561987651340";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcessadorDeWebhookEntrada processador;

    @BeforeEach
    void preparar() {
        limpar();
    }

    @AfterEach
    void restaurar() {
        limpar();
    }

    @Test
    @DisplayName("payload literal de produção grava list_reply e button_reply como LEAD no histórico")
    void payloadDeProducao_gravaTitulosNoHistoricoComoLead() {
        String payload =
                """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"%s"},
                  "contacts":[{"profile":{"name":"Cliente E134"},"wa_id":"%s"}],
                  "messages":[
                    {"from":"%s","id":"%slista","timestamp":"1756839300","type":"interactive",
                     "interactive":{"type":"list_reply",
                       "list_reply":{"id":"ev03_atendente_6701a2f8-abcd-4123-8def-0123456789ab","title":"Michael"}}},
                    {"from":"%s","id":"%sbotao","timestamp":"1756839400","type":"interactive",
                     "interactive":{"type":"button_reply",
                       "button_reply":{"id":"ev08_avaliacao_bom","title":"Bom"}}}
                  ]
                }}]}]}
                """
                        .formatted(
                                PHONE_NUMBER_ID,
                                TELEFONE,
                                TELEFONE,
                                PREFIXO_ID,
                                TELEFONE,
                                PREFIXO_ID)
                        .strip();

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        List<Map<String, Object>> mensagens = jdbc.queryForList(
                """
                SELECT m.conteudo, m.remetente_tipo, m.tipo
                FROM mensagem m
                JOIN atendimento a ON a.id = m.atendimento_id
                JOIN lead l ON l.id = a.lead_id
                WHERE l.telefone = ?
                ORDER BY m.enviado_em, m.id
                """,
                TELEFONE);

        assertThat(mensagens).hasSize(2);
        assertThat(mensagens.get(0).get("conteudo")).isEqualTo("Michael");
        assertThat(mensagens.get(0).get("remetente_tipo")).isEqualTo("LEAD");
        assertThat(mensagens.get(0).get("tipo")).isEqualTo("TEXTO");
        assertThat(mensagens.get(1).get("conteudo")).isEqualTo("Bom");
        assertThat(mensagens.get(1).get("remetente_tipo")).isEqualTo("LEAD");
        assertThat(mensagens.get(1).get("tipo")).isEqualTo("TEXTO");
    }

    private ResponseEntity<Void> postar(String payload) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", assinatura(payload));
        return http.postForEntity(
                "/webhook/canal", new HttpEntity<>(payload, cabecalhos), Void.class);
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
        jdbc.update(
                "DELETE FROM outbox_evento WHERE tipo = 'automacao.webhook.repassar'"
                        + " AND payload->>'payloadCru' LIKE ?",
                "%" + PREFIXO_ID + "%");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE ?", PREFIXO_ID + "%");
        jdbc.update(
                "DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE ?", PREFIXO_ID + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone = ?)",
                TELEFONE);
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE telefone = ?)",
                TELEFONE);
        jdbc.update("DELETE FROM lead WHERE telefone = ?", TELEFONE);
    }
}
