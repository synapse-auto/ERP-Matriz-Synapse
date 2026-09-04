package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-secret=" + WebhookMetaLocalizacaoIT.APP_SECRET,
            "synapse.canal.whatsapp.webhook-verify-token=verify-loc",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class WebhookMetaLocalizacaoIT extends PostgresIT {

    static final String APP_SECRET = "segredo-loc-para-assinatura-do-webhook";
    private static final String PHONE_NUMBER_ID = "phone-number-id-teste";
    private static final String WABA_ID = "waba-id-teste";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProcessadorDeWebhookEntrada processador;

    private UUID leadId;

    @BeforeEach
    void setup() {
        UUID canalId = UUID.randomUUID();
        UUID credencialId = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canalId, "Canal Teste");
        jdbc.update(
                "INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo) VALUES (?, ?, ?, ?, ?, ?)",
                credencialId, canalId, "5561999999999", PHONE_NUMBER_ID, "token-qualquer", true);

        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo) VALUES (?, 'IA', 'ia@local', 'xxx', 'ATENDENTE', true) ON CONFLICT DO NOTHING",
                UUID.fromString("56c5270c-2d0f-4889-8d76-e8ebde78ecaa"));

        leadId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, telefone, nome) VALUES (?, ?, ?)",
                leadId, "5561999999999", "Cliente Teste");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM mensagem_id_externo");
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia");
        jdbc.update("DELETE FROM mensagem");
        jdbc.update("DELETE FROM atendimento");
        jdbc.update("DELETE FROM lead");
        jdbc.update("DELETE FROM canal_credencial");
        jdbc.update("DELETE FROM canal");
        jdbc.update("DELETE FROM webhook_entrada");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '%@teste.local' OR email = 'ia@local'");
    }

    @Test
    void recebeProcessaEDisponibilizaLocalizacao() throws Exception {
        String payload = """
                {
                  "entry": [{
                    "id": "%s",
                    "changes": [{
                      "value": {
                        "metadata": {
                          "phone_number_id": "%s"
                        },
                        "contacts": [{
                          "wa_id": "5561999999999",
                          "profile": { "name": "Cliente teste" }
                        }],
                        "messages": [{
                          "from": "5561999999999",
                          "id": "wamid.location.teste",
                          "timestamp": "1720000000",
                          "type": "location",
                          "location": {
                            "latitude": -7.115,
                            "longitude": -34.864,
                            "name": "Condominio Park Cowboy",
                            "address": "R. Dr. Valdevino, 800"
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(WABA_ID, PHONE_NUMBER_ID);

        ResponseEntity<String> resposta = postar(payload, assinatura(payload));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Idempotency test (post same payload again)
        ResponseEntity<String> respostaIdempotente = postar(payload, assinatura(payload));
        assertThat(respostaIdempotente.getStatusCode()).isEqualTo(HttpStatus.OK);

        processador.processarPendentes();

        List<Map<String, Object>> mensagens = jdbc.queryForList("SELECT id, atendimento_id, tipo::text, conteudo, midia_metadados FROM mensagem WHERE tipo::text = 'LOCALIZACAO'");
        assertThat(mensagens).hasSize(1);

        Map<String, Object> msg = mensagens.get(0);
        Object metadadosObj = msg.get("midia_metadados");
        String metadados = metadadosObj instanceof org.postgresql.util.PGobject pgobj
                ? pgobj.getValue()
                : (String) metadadosObj;
        assertThat(metadados).contains("-7.115").contains("Park Cowboy");

        UUID atendimentoId = (UUID) msg.get("atendimento_id");

        UUID atendenteAId = UUID.randomUUID();
        UUID atendenteBId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo, senha_alterada_em)"
                        + " VALUES (?, 'Admin', 'admin@teste.local',"
                        + " '$2a$10$5vISVeL7I/o7K8rKLvXFDOko5iYacVlYlvxIJqTywAoLzf2eP6dPK', 'GESTOR', true, now())"
                        + " ON CONFLICT DO NOTHING",
                UUID.randomUUID());
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo, senha_alterada_em)"
                        + " VALUES (?, 'Atendente A', 'a@teste.local',"
                        + " '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42', 'ATENDENTE', true, now())"
                        + " ON CONFLICT DO NOTHING",
                atendenteAId);
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo, senha_alterada_em)"
                        + " VALUES (?, 'Atendente B', 'b@teste.local',"
                        + " '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42', 'ATENDENTE', true, now())"
                        + " ON CONFLICT DO NOTHING",
                atendenteBId);

        // Atribui o atendimento ao Atendente A em EM_ATENDIMENTO (RN-CRM-01 / RLS)
        jdbc.update("UPDATE atendimento SET status = 'EM_ATENDIMENTO'::status_atendimento, atendente_id = ? WHERE id = ?", atendenteAId, atendimentoId);
        jdbc.update("UPDATE lead SET status_basico = 'EM_ATENDIMENTO'::status_basico_lead, atendente_responsavel_id = ? WHERE id = ?", atendenteAId, leadId);

        // Visibility Check 1: Gestor vê tudo (200 OK)
        String adminToken = com.synapse.crm.app.seguranca.ApoioAutenticacao
                .login(http, "admin@teste.local", "gestor123").accessToken();
        ResponseEntity<Map> respostaApi = com.synapse.crm.app.seguranca.ApoioAutenticacao.comToken(
                http, adminToken, org.springframework.http.HttpMethod.GET,
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens", Map.class);

        assertThat(respostaApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) respostaApi.getBody().get("mensagens");
        assertThat(itens).isNotNull();
        assertThat(itens).hasSize(1);
        Map<String, Object> msgApi = itens.get(0);
        assertThat(msgApi.get("tipo")).isEqualTo("LOCALIZACAO");
        assertThat(msgApi.get("midiaMetadados").toString()).contains("-7.115").contains("Park Cowboy");

        // Visibility Check 2: Atendente A (responsável) vê o atendimento (200 OK)
        String aToken = com.synapse.crm.app.seguranca.ApoioAutenticacao
                .login(http, "a@teste.local", "atendente123").accessToken();
        ResponseEntity<Map> respostaApiA = com.synapse.crm.app.seguranca.ApoioAutenticacao.comToken(
                http, aToken, org.springframework.http.HttpMethod.GET,
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens", Map.class);
        assertThat(respostaApiA.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Visibility Check 3: Atendente B (sem vínculo) recebe 404 NOT_FOUND (RN-CRM-01 / RLS)
        String bToken = com.synapse.crm.app.seguranca.ApoioAutenticacao
                .login(http, "b@teste.local", "atendente123").accessToken();
        ResponseEntity<Map> respostaApiB = com.synapse.crm.app.seguranca.ApoioAutenticacao.comToken(
                http, bToken, org.springframework.http.HttpMethod.GET,
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens", Map.class);
        assertThat(respostaApiB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Location without name/address
        String payloadSemNome = """
                {
                  "entry": [{
                    "id": "%s",
                    "changes": [{
                      "value": {
                        "metadata": { "phone_number_id": "%s" },
                        "contacts": [{ "wa_id": "5561999999999", "profile": { "name": "Cliente" } }],
                        "messages": [{
                          "from": "5561999999999",
                          "id": "wamid.location.noname",
                          "timestamp": "1720000001",
                          "type": "location",
                          "location": {
                            "latitude": -8.0,
                            "longitude": -35.0
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(WABA_ID, PHONE_NUMBER_ID);
        postar(payloadSemNome, assinatura(payloadSemNome));

        // Invalid coordinates
        String payloadInvalido = """
                {
                  "entry": [{
                    "id": "%s",
                    "changes": [{
                      "value": {
                        "metadata": { "phone_number_id": "%s" },
                        "contacts": [{ "wa_id": "5561999999999", "profile": { "name": "Cliente" } }],
                        "messages": [{
                          "from": "5561999999999",
                          "id": "wamid.location.invalid",
                          "timestamp": "1720000002",
                          "type": "location",
                          "location": {
                            "latitude": 95.0,
                            "longitude": -35.0
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(WABA_ID, PHONE_NUMBER_ID);
        postar(payloadInvalido, assinatura(payloadInvalido));

        processador.processarPendentes();

        List<Map<String, Object>> allMensagens = jdbc.queryForList("SELECT id FROM mensagem WHERE tipo::text = 'LOCALIZACAO'");
        // Only 2 expected: the first one and the one without name/address. Invalid is dropped.
        assertThat(allMensagens).hasSize(2);
    }

    private ResponseEntity<String> postar(String payload, String assinatura) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", assinatura);
        return http.postForEntity("/webhook/canal", new HttpEntity<>(payload, headers), String.class);
    }

    private String assinatura(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
