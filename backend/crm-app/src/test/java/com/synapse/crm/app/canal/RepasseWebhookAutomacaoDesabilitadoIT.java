package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.automacao.repasse-webhook.url=",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class RepasseWebhookAutomacaoDesabilitadoIT extends PostgresIT {

    private static final String PREFIXO = "E25-SEM-REPASSE-";
    private static final String TELEFONE = "5561977771111";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcessadorDeWebhookEntrada processador;

    private UUID leadId;

    @BeforeEach
    void preparar() {
        limpar();
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

    @Test
    @DisplayName("sem URL configurada o webhook entra normalmente e nenhum repasse e enfileirado")
    void semDestino_naoRepassaENaoQuebra() {
        String payload =
                "{\"id\":\"ext-E25-sem-url\",\"de\":\"" + TELEFONE
                        + "\",\"nome\":\"Cliente\",\"texto\":\"oi\"}";
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", CanalFake.ASSINATURA_VALIDA);

        assertThat(http.postForEntity(
                                "/webhook/canal",
                                new HttpEntity<>(payload, cabecalhos),
                                Void.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(mensagensDoLead()).isEqualTo(1);
            assertThat(repasses()).isZero();
        });
    }

    private int mensagensDoLead() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id=m.atendimento_id WHERE a.lead_id=?",
                Integer.class,
                leadId);
    }

    private int repasses() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo='automacao.webhook.repassar'",
                Integer.class);
    }

    private void limpar() {
        jdbc.update("DELETE FROM outbox_evento WHERE tipo='automacao.webhook.repassar'");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo='ext-E25-sem-url'");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }
}
