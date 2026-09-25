package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Grupo nao e conversa do CRM (docs/44). Na Uzapi o {@code from} de uma mensagem de grupo e o
 * participante; sem filtro, ela caia no atendimento individual dele e ia para a Automacao, que
 * responde no privado.
 *
 * <p>Corpo no schema do Swagger oficial ({@code isGroup} obrigatorio), dados sinteticos. O repasse a
 * Automacao esta configurado (porta fechada, nada e entregue): a prova de "nao responde no privado"
 * e nao existir intencao de repasse na outbox para o POST so de grupo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=uzapi-autotic",
            "synapse.canal.whatsapp.url-base=http://127.0.0.1:1",
            "synapse.canal.whatsapp.numero-principal=phone-id-grupo-uzapi",
            "synapse.canal.whatsapp.token=token-de-teste",
            "synapse.canal.whatsapp.versao-api=v1",
            "synapse.canal.whatsapp.webhook-secret=" + WebhookGrupoUzapiIT.SEGREDO,
            "synapse.canal.foto-perfil.habilitado=false",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class WebhookGrupoUzapiIT extends PostgresIT {

    static final String SEGREDO = "segredo-grupo-uzapi";
    private static final String PHONE_NUMBER_ID = "phone-id-grupo-uzapi";
    private static final String PARTICIPANTE = "5561977770013";
    private static final String PREFIXO = "GRUPO-UZAPI-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProcessadorDeWebhookEntrada processador;

    private UUID canalId;
    private UUID credencialId;
    private UUID leadId;
    private UUID atendimentoId;

    @BeforeEach
    void preparar() {
        canalId = UUID.randomUUID();
        credencialId = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canalId, "Canal Grupo Uzapi");
        jdbc.update(
                "INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                credencialId, canalId, "5543900000013", PHONE_NUMBER_ID, "token-qualquer", true);
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo) VALUES (?, 'IA', 'ia@local',"
                        + " 'xxx', 'ATENDENTE', true) ON CONFLICT DO NOTHING",
                UUID.fromString("56c5270c-2d0f-4889-8d76-e8ebde78ecaa"));
        // O participante ja e cliente com conversa individual aberta: e nela que o grupo vazava.
        leadId = UUID.randomUUID();
        atendimentoId = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, telefone, nome) VALUES (?, ?, 'Participante')", leadId, PARTICIPANTE);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em) VALUES (?, ?, 'EM_ATENDIMENTO', now())",
                atendimentoId, leadId);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM outbox_evento WHERE tipo = 'automacao.webhook.repassar'"
                + " AND payload->>'payloadCru' LIKE ?", "%" + PREFIXO + "%");
        jdbc.update(
                "DELETE FROM mensagem_id_externo WHERE mensagem_id IN (SELECT m.id FROM mensagem m"
                        + " JOIN atendimento a ON a.id = m.atendimento_id WHERE a.lead_id = ?)",
                leadId);
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", leadId);
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE telefone = ?)", PARTICIPANTE);
        jdbc.update("DELETE FROM lead WHERE telefone = ?", PARTICIPANTE);
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM canal_credencial WHERE id = ?", credencialId);
        jdbc.update("DELETE FROM canal WHERE id = ?", canalId);
    }

    @Test
    @DisplayName("grupo não entra na conversa do participante, não vai à Automação e fica visível como descarte")
    void somenteGrupo_naoAssociaAoPrivadoNaoRepassaERegistraDescarte() {
        String grupo = payload(mensagemDeGrupo(PREFIXO + "1", "#reset"));

        assertThat(postar(grupo, "segredo-errado").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postar(grupo, SEGREDO).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();
        // Reentrega: continua sem mensagem, sem repasse e com uma linha só.
        assertThat(postar(grupo, SEGREDO).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(mensagensDoParticipante()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE telefone = ?", Integer.class, PARTICIPANTE))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM atendimento WHERE lead_id = ?", Integer.class, leadId)).isEqualTo(1);
        assertThat(repassesComId(PREFIXO + "1")).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE id_externo = ?", Integer.class, PREFIXO + "1"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT itens_descartados FROM webhook_entrada WHERE id_externo = ?", Integer.class, PREFIXO + "1"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT descartes::text FROM webhook_entrada WHERE id_externo = ?", String.class, PREFIXO + "1"))
                .contains("GRUPO_NAO_SUPORTADO")
                .doesNotContain(PARTICIPANTE)
                .doesNotContain("#reset");
    }

    @Test
    @DisplayName("POST misto: a mensagem privada entra, a de grupo vira descarte e o POST segue para a Automação")
    void postMisto_privadoEntraGrupoDescartadoERepasseMantido() {
        String misto = payload(mensagemDeGrupo(PREFIXO + "2", "no grupo") + ","
                + """
                {"from":"%s","id":"%s","isGroup":false,"timestamp":"1768843400","type":"text",
                 "text":{"body":"no privado"}}
                """.formatted(PARTICIPANTE, PREFIXO + "3"));

        assertThat(postar(misto, SEGREDO).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(jdbc.queryForList(
                "SELECT m.conteudo FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                        + " WHERE a.lead_id = ?",
                String.class, leadId)).containsExactly("no privado");
        assertThat(jdbc.queryForObject(
                "SELECT descartes::text FROM webhook_entrada WHERE id_externo = ?", String.class, PREFIXO + "2"))
                .contains("GRUPO_NAO_SUPORTADO");
        // Limite documentado: o corpo assinado nao e reescrito, entao o POST misto segue inteiro.
        assertThat(repassesComId(PREFIXO + "3")).isEqualTo(1);
    }

    private int mensagensDoParticipante() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone = ?",
                Integer.class, PARTICIPANTE);
    }

    private int repassesComId(String idExterno) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo = 'automacao.webhook.repassar'"
                        + " AND payload->>'payloadCru' LIKE ?",
                Integer.class, "%" + idExterno + "%");
    }

    private static String mensagemDeGrupo(String id, String texto) {
        return """
                {"from":"%s","id":"%s","isGroup":true,"timestamp":"1768843300","type":"text",
                 "text":{"body":"%s"}}
                """.formatted(PARTICIPANTE, id, texto);
    }

    private static String payload(String mensagens) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"","changes":[{"value":{
                  "messaging_product":"whatsapp",
                  "metadata":{"display_phone_number":"5543900000013","phone_number_id":"%s"},
                  "contacts":[{"profile":{"name":"Participante"},"wa_id":"%s"}],
                  "messages":[%s]
                },"field":"messages"}]}]}
                """.formatted(PHONE_NUMBER_ID, PARTICIPANTE, mensagens);
    }

    private ResponseEntity<String> postar(String payload, String segredo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(
                "/webhook/canal?secret={segredo}", new HttpEntity<>(payload, headers), String.class, segredo);
    }
}
