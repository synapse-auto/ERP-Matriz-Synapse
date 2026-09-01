package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

/**
 * O retry publica somente a mensagem já aceita: nunca pode repetir a transição humana feita no
 * endpoint de template.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000"
        })
class TemplateManualOutboxIT extends PostgresIT {

    private static final String PREFIXO = "template-manual-outbox-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PublicadorDaOutbox publicador;

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update(
                "DELETE FROM outbox_evento WHERE payload->>'atendimentoId' IN "
                        + "(SELECT id::text FROM atendimento WHERE lead_id IN "
                        + "(SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN "
                        + "(SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("publicar/repetir a outbox do template não repete transferência nem auditoria")
    void publicarOutboxDoTemplate_naoRepeteEfeitosDaAcaoHumana() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, status_basico, ultima_interacao_em) VALUES (?, ?, 'IA', now())",
                leadId,
                PREFIXO + UUID.randomUUID());
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em) VALUES (?, ?, 'EM_IA', now())",
                atendimentoId,
                leadId);

        var resposta = enviarTemplate(leadId);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(contar("evento_timeline", leadId, "tipo", "LEAD_TRANSFERIDO_POR_ENVIO")).isOne();
            assertThat(contar("audit_log", leadId, "acao", "ENVIO_COM_TRANSFERENCIA_DE_LEAD")).isOne();
        });

        publicador.publicarPendentes();
        publicador.publicarPendentes();

        assertThat(contar("evento_timeline", leadId, "tipo", "LEAD_TRANSFERIDO_POR_ENVIO")).isOne();
        assertThat(contar("audit_log", leadId, "acao", "ENVIO_COM_TRANSFERENCIA_DE_LEAD")).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isOne();
    }

    private org.springframework.http.ResponseEntity<String> enviarTemplate(UUID leadId) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "/api/v1/atendimentos/mensagens/template",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "leadId", leadId.toString(),
                                "nome", "reativacao",
                                "idioma", "pt_BR",
                                "parametros", List.of("Cliente")),
                        cabecalhos),
                String.class);
    }

    private long contar(String tabela, UUID leadId, String coluna, String valor) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE lead_id = ? AND " + coluna + " = ?",
                Long.class,
                leadId,
                valor);
    }
}
