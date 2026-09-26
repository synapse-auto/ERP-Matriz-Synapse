package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

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
 * E214 — reacao do cliente pela Uzapi/Autotic, schema {@code ReactionMessage} do Swagger oficial,
 * do POST com {@code ?secret} ate a linha gravada. Dados sinteticos; provedor em porta fechada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=uzapi-autotic",
            "synapse.canal.whatsapp.url-base=http://127.0.0.1:1",
            "synapse.canal.whatsapp.numero-principal=phone-id-reacao-uzapi",
            "synapse.canal.whatsapp.token=token-de-teste",
            "synapse.canal.whatsapp.versao-api=v1",
            "synapse.canal.whatsapp.webhook-secret=" + WebhookReacaoDoClienteUzapiIT.SEGREDO,
            "synapse.canal.foto-perfil.habilitado=false",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class WebhookReacaoDoClienteUzapiIT extends PostgresIT {

    static final String SEGREDO = "segredo-reacao-uzapi";
    private static final String PHONE_NUMBER_ID = "phone-id-reacao-uzapi";
    private static final String TELEFONE_CLIENTE = "5561977772140";
    private static final String PREFIXO = "REACAO-UZAPI-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProcessadorDeWebhookEntrada processador;

    private UUID canalId;
    private UUID credencialId;
    private UUID leadId;
    private UUID mensagemId;

    @BeforeEach
    void preparar() {
        canalId = UUID.randomUUID();
        credencialId = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canalId, "Canal Reacao Uzapi");
        jdbc.update(
                "INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                credencialId, canalId, "5543900002140", PHONE_NUMBER_ID, "token-qualquer", true);
        leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        mensagemId = UUID.randomUUID();
        Instant enviadaEm = Instant.parse("2026-09-20T12:00:00Z");
        jdbc.update("INSERT INTO lead (id, telefone, nome) VALUES (?, ?, 'Cliente Uzapi')", leadId, TELEFONE_CLIENTE);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em) VALUES (?, ?, 'EM_ATENDIMENTO', now())",
                atendimentoId, leadId);
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, 'LEAD', 'TEXTO', 'bom dia', 'ENTREGUE', ?)",
                mensagemId, atendimentoId, Timestamp.from(enviadaEm));
        jdbc.update(
                "INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)"
                        + " VALUES (?, ?, ?, ?)",
                PREFIXO + "alvo", mensagemId, Timestamp.from(enviadaEm), atendimentoId);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM outbox_evento WHERE tipo = 'automacao.webhook.repassar'"
                + " AND payload->>'payloadCru' LIKE ?", "%" + PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", leadId);
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE telefone = ?)", TELEFONE_CLIENTE);
        jdbc.update("DELETE FROM lead WHERE telefone = ?", TELEFONE_CLIENTE);
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM canal_credencial WHERE id = ?", credencialId);
        jdbc.update("DELETE FROM canal WHERE id = ?", canalId);
    }

    @Test
    @DisplayName("reação da Uzapi: segredo, aplicação, reentrega, alvo desconhecido e remoção")
    void reacaoUzapi_pontaAPonta() {
        String reacao = payload(reacao("1", "alvo", "🙏", 1768843338L));

        assertThat(postar(reacao, "segredo-errado").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE id_externo LIKE ?", Integer.class, PREFIXO + "%"))
                .isZero();

        assertThat(postar(reacao, SEGREDO).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postar(reacao, SEGREDO).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();
        assertThat(emoji()).isEqualTo("🙏");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_reacao_cliente WHERE mensagem_id = ?",
                Integer.class, mensagemId)).isEqualTo(1);

        postar(payload(reacao("2", "nao-existe", "😮", 1768843400L)), SEGREDO);
        processador.processarPendentes();
        assertThat(emoji()).isEqualTo("🙏");
        assertThat(jdbc.queryForObject(
                "SELECT descartes::text FROM webhook_entrada WHERE id_externo = ?", String.class, PREFIXO + "2"))
                .contains("ALVO_DESCONHECIDO");
        // Reacao nunca vira mensagem nem cria atendimento.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id WHERE a.lead_id = ?",
                Integer.class, leadId)).isEqualTo(1);

        postar(payload(reacao("3", "alvo", "", 1768843500L)), SEGREDO);
        processador.processarPendentes();
        assertThat(emoji()).isNull();
    }

    private String emoji() {
        return jdbc.queryForObject(
                "SELECT emoji FROM mensagem_reacao_cliente WHERE mensagem_id = ?", String.class, mensagemId);
    }

    private static String reacao(String id, String alvo, String emoji, long quando) {
        return """
                {"from":"%s","id":"%s%s","isGroup":false,"timestamp":"%d","type":"reaction",
                 "reaction":{"emoji":"%s","message_id":"%s%s"}}
                """.formatted(TELEFONE_CLIENTE, PREFIXO, id, quando, emoji, PREFIXO, alvo);
    }

    private static String payload(String mensagens) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"","changes":[{"value":{
                  "messaging_product":"whatsapp",
                  "metadata":{"display_phone_number":"5543900002140","phone_number_id":"%s"},
                  "contacts":[{"profile":{"name":"Cliente Uzapi"},"wa_id":"%s"}],
                  "messages":[%s]
                },"field":"messages"}]}]}
                """.formatted(PHONE_NUMBER_ID, TELEFONE_CLIENTE, mensagens);
    }

    private ResponseEntity<String> postar(String payload, String segredo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(
                "/webhook/canal?secret={segredo}", new HttpEntity<>(payload, headers), String.class, segredo);
    }
}
