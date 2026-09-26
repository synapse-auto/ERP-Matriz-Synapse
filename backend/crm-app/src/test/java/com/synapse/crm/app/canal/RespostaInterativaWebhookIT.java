package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
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
    private static final String EMAIL_GESTOR = "e212-botao-gestor@teste.local";

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

    @Test
    @DisplayName("clique em botão de template (type=button) entra no histórico citando o template, sem perder o lote")
    void cliqueEmBotaoDeTemplate_chegaAoHistoricoComContextoEPreservaOLote() {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        Instant templateEnviadoEm = Instant.parse("2026-09-20T12:00:00Z");
        UUID gestorId = criarGestor();
        jdbc.update("INSERT INTO lead (id, nome, telefone) VALUES (?, 'Cliente E212', ?)", leadId, TELEFONE);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em) VALUES (?, ?, 'EM_ATENDIMENTO', now())",
                atendimentoId, leadId);
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, status_entrega,"
                        + " enviado_em) VALUES (?, ?, 'ATENDENTE', ?, 'TEXTO', 'Podemos confirmar a visita?',"
                        + " 'ENTREGUE', ?)",
                templateId, atendimentoId, gestorId, Timestamp.from(templateEnviadoEm));
        jdbc.update(
                "INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)"
                        + " VALUES (?, ?, ?, ?)",
                PREFIXO_ID + "template", templateId, Timestamp.from(templateEnviadoEm), atendimentoId);

        // Lote misto: clique válido, clique sem texto (descartado) e texto comum.
        String payload =
                """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"%1$s"},
                  "contacts":[{"profile":{"name":"Cliente E212"},"wa_id":"%2$s"}],
                  "messages":[
                    {"context":{"from":"556130000000","id":"%3$stemplate"},
                     "from":"%2$s","id":"%3$sclique","timestamp":"1758369700","type":"button",
                     "button":{"payload":"CONFIRMAR_VISITA","text":"Confirmar visita"}},
                    {"from":"%2$s","id":"%3$svazio","timestamp":"1758369701","type":"button",
                     "button":{"payload":"SO_PAYLOAD","text":""}},
                    {"from":"%2$s","id":"%3$stexto","timestamp":"1758369702","type":"text",
                     "text":{"body":"até amanhã"}}
                  ]
                }}]}]}
                """
                        .formatted(PHONE_NUMBER_ID, TELEFONE, PREFIXO_ID)
                        .strip();

        assertThat(postarComAssinatura(payload, "sha256=" + "0".repeat(64)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE id_externo LIKE ?", Integer.class, PREFIXO_ID + "%"))
                .isZero();

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();
        // Reentrega do provedor depois de processada: não duplica nada.
        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        List<Map<String, Object>> recebidas = jdbc.queryForList(
                """
                SELECT m.id, m.atendimento_id, m.conteudo, m.remetente_tipo, m.tipo::text AS tipo, m.enviado_em
                FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id
                WHERE a.lead_id = ? AND m.remetente_tipo = 'LEAD'
                ORDER BY m.enviado_em
                """,
                leadId);
        assertThat(recebidas).extracting(linha -> linha.get("conteudo"))
                .containsExactly("Confirmar visita", "até amanhã");
        assertThat(recebidas.get(0).get("tipo")).isEqualTo("TEXTO");
        // Recebida grava enviado_em com a hora do processamento (regra de todo o recebimento, não do
        // clique); a ordem do lote é o que o histórico precisa preservar.
        assertThat(((Timestamp) recebidas.get(0).get("enviado_em")).toInstant())
                .isBefore(((Timestamp) recebidas.get(1).get("enviado_em")).toInstant());
        assertThat(jdbc.queryForObject(
                "SELECT itens_descartados FROM webhook_entrada WHERE id_externo = ?", Integer.class,
                PREFIXO_ID + "clique")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT descartes::text FROM webhook_entrada WHERE id_externo = ?", String.class,
                PREFIXO_ID + "clique"))
                .contains("button").contains("CONTEUDO_INVALIDO").doesNotContain("SO_PAYLOAD");

        Map<String, Object> clique = mensagemPelaApi(
                (UUID) recebidas.get(0).get("atendimento_id"), (UUID) recebidas.get(0).get("id"));
        assertThat(clique.get("conteudo")).isEqualTo("Confirmar visita");
        assertThat(clique.get("remetenteTipo")).isEqualTo("LEAD");
        @SuppressWarnings("unchecked")
        Map<String, Object> citacao = (Map<String, Object>) clique.get("citacao");
        assertThat(citacao).isNotNull();
        assertThat(citacao.get("origemId")).isEqualTo(templateId.toString());
        assertThat(citacao.get("tipoReferencia")).isEqualTo("RESPOSTA");
    }

    private UUID criarGestor() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo, senha_alterada_em)"
                        + " VALUES (?, 'Gestor E212', ?,"
                        + " '$2a$10$5vISVeL7I/o7K8rKLvXFDOko5iYacVlYlvxIJqTywAoLzf2eP6dPK', 'GESTOR', true, now())",
                id, EMAIL_GESTOR);
        return id;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> mensagemPelaApi(UUID atendimentoId, UUID mensagemId) {
        String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, "gestor123").accessToken();
        ResponseEntity<Map> resposta = ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/atendimentos/" + atendimentoId + "/mensagens", Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) resposta.getBody().get("mensagens");
        return mensagens.stream()
                .filter(mensagem -> mensagemId.toString().equals(mensagem.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private ResponseEntity<Void> postar(String payload) {
        return postarComAssinatura(payload, assinatura(payload));
    }

    private ResponseEntity<Void> postarComAssinatura(String payload, String assinatura) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", assinatura);
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
        jdbc.update("DELETE FROM usuario WHERE email = ?", EMAIL_GESTOR);
    }
}
