package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

/** Prova o isolamento pelo ponto de entrada HTTP real, antes das duas copias do payload cru. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-secret=" + IsolamentoCanalWebhookIT.APP_SECRET,
            "synapse.canal.whatsapp.webhook-verify-token=verify-e27",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class IsolamentoCanalWebhookIT extends PostgresIT {

    static final String APP_SECRET = "segredo-e27-para-assinatura-do-webhook";
    private static final String CREDENCIAL_ID = "cc000000-0000-4000-8000-000000000001";
    private static final String PHONE_NUMBER_ID = "1307417749115229";
    private static final String OUTRO_PHONE_NUMBER_ID = "111111111111111";
    private static final String PREFIXO_ID = "wamid.E27-";
    private static final String PREFIXO_TELEFONE = "5561987600";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcessadorDeWebhookEntrada processador;

    @BeforeEach
    void preparar() {
        limpar();
        jdbc.update(
                "UPDATE canal_credencial SET identificador_externo = ?, ativo = TRUE,"
                        + " vigente_ate = NULL WHERE id = ?::uuid",
                PHONE_NUMBER_ID,
                CREDENCIAL_ID);
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
    @DisplayName("outro numero responde 200 sem lead, atendimento, webhook ou repasse")
    void outroNumero_descartaAntesDeQualquerPersistencia(CapturedOutput log) {
        String payload = payload(
                mudanca(OUTRO_PHONE_NUMBER_ID, PREFIXO_ID + "outro", PREFIXO_TELEFONE + "01", "nao guardar"));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(leads()).isZero();
        assertThat(atendimentos()).isZero();
        assertThat(webhooks()).isZero();
        assertThat(repasses()).isZero();
        assertThat(log.getOut())
                .contains("Webhook de outro canal descartado")
                .contains(OUTRO_PHONE_NUMBER_ID)
                .contains("eventos=1")
                .doesNotContain("nao guardar");
    }

    @Test
    @DisplayName("numero cadastrado preserva entrada, repasse, lead e atendimento")
    void numeroCadastrado_mantemFluxoIntegro() {
        String payload = payload(
                mudanca(PHONE_NUMBER_ID, PREFIXO_ID + "aceito", PREFIXO_TELEFONE + "02", "mensagem legitima"));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(webhooks()).isEqualTo(1);
        assertThat(repasses()).isEqualTo(1);

        processador.processarPendentes();

        assertThat(leads()).isEqualTo(1);
        assertThat(atendimentos()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT a.canal_id FROM atendimento a JOIN lead l ON l.id = a.lead_id"
                                + " WHERE l.telefone = ?",
                        UUID.class,
                        PREFIXO_TELEFONE + "02"))
                .isEqualTo(jdbc.queryForObject(
                        "SELECT canal_id FROM canal_credencial WHERE id = ?::uuid",
                        UUID.class,
                        CREDENCIAL_ID));
    }

    @Test
    @DisplayName("ponto de entrada drena todas as mensagens de uma change na ordem")
    void pontoEntrada_variasMensagensNaMesmaChange_gravaTodas() {
        String telefone = PREFIXO_TELEFONE + "10";
        String payload = payload(mudancaComMensagens(
                PHONE_NUMBER_ID,
                "{\"wa_id\":\"%s\",\"profile\":{\"name\":\"Cliente dez\"}}".formatted(telefone),
                mensagem(telefone, PREFIXO_ID + "10-a", "a") + ","
                        + mensagem(telefone, PREFIXO_ID + "10-b", "b") + ","
                        + mensagem(telefone, PREFIXO_ID + "10-c", "c")));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                                + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone = ?",
                        Integer.class,
                        telefone))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("ponto de entrada percorre duas entries do mesmo canal")
    void pontoEntrada_duasEntriesDoMesmoCanal_gravaAmbas() {
        String telefoneA = PREFIXO_TELEFONE + "11";
        String telefoneB = PREFIXO_TELEFONE + "12";
        String payload = payload(
                mudanca(PHONE_NUMBER_ID, PREFIXO_ID + "11", telefoneA, "a"),
                mudanca(PHONE_NUMBER_ID, PREFIXO_ID + "12", telefoneB, "b"));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(mensagensDoTelefone(telefoneA)).isEqualTo(1);
        assertThat(mensagensDoTelefone(telefoneB)).isEqualTo(1);
    }

    @Test
    @DisplayName("cada remetente recebe o nome do próprio contato")
    void pontoEntrada_contatosDiferentes_resolveNomePorWaId() {
        String telefoneA = PREFIXO_TELEFONE + "15";
        String telefoneB = PREFIXO_TELEFONE + "16";
        String contatos = "{\"wa_id\":\"%s\",\"profile\":{\"name\":\"Ana quinze\"}},"
                .formatted(telefoneA)
                + "{\"wa_id\":\"%s\",\"profile\":{\"name\":\"Bruno dezesseis\"}}"
                        .formatted(telefoneB);
        String mensagens = mensagem(telefoneB, PREFIXO_ID + "16", "b") + ","
                + mensagem(telefoneA, PREFIXO_ID + "15", "a");

        assertThat(postar(payload(mudancaComMensagens(PHONE_NUMBER_ID, contatos, mensagens)))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(jdbc.queryForObject(
                        "SELECT nome FROM lead WHERE telefone = ?", String.class, telefoneA))
                .isEqualTo("Ana quinze");
        assertThat(jdbc.queryForObject(
                        "SELECT nome FROM lead WHERE telefone = ?", String.class, telefoneB))
                .isEqualTo("Bruno dezesseis");
    }

    @Test
    @DisplayName("status na primeira posição não impede a mensagem seguinte")
    void pontoEntrada_statusAntesDaMensagem_naoDescartaPayload() {
        String telefone = PREFIXO_TELEFONE + "13";
        String status = "{\"from\":\"%s\",\"id\":\"%s-status\",\"type\":\"status\"}"
                .formatted(telefone, PREFIXO_ID + "13");
        String payload = payload(mudancaComMensagens(
                PHONE_NUMBER_ID,
                "{\"wa_id\":\"%s\",\"profile\":{\"name\":\"Cliente treze\"}}".formatted(telefone),
                status + "," + mensagem(telefone, PREFIXO_ID + "13-msg", "mensagem")));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(mensagensDoTelefone(telefone)).isEqualTo(1);
    }

    @Test
    @DisplayName("reentrega reagrupada não duplica a mensagem B")
    void pontoEntrada_reentregaReagrupada_deduplicaPorWamid() {
        String telefone = PREFIXO_TELEFONE + "14";
        String a = mensagem(telefone, PREFIXO_ID + "14-a", "a");
        String b = mensagem(telefone, PREFIXO_ID + "14-b", "b");
        String contatos = "{\"wa_id\":\"%s\",\"profile\":{\"name\":\"Cliente quatorze\"}}".formatted(telefone);

        assertThat(postar(payload(mudancaComMensagens(PHONE_NUMBER_ID, contatos, a + "," + b)))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(postar(payload(mudancaComMensagens(PHONE_NUMBER_ID, contatos, b)))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        processador.processarPendentes();

        assertThat(mensagensDoTelefone(telefone)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem_recebida_idempotencia WHERE wamid = ?",
                        Integer.class,
                        PREFIXO_ID + "14-b"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reentrega do mesmo POST não duplica a mensagem")
    void pontoEntrada_reentregaDoMesmoPost_deduplica() {
        String telefone = PREFIXO_TELEFONE + "17";
        String payload = payload(mudanca(PHONE_NUMBER_ID, PREFIXO_ID + "17", telefone, "uma vez"));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(mensagensDoTelefone(telefone)).isEqualTo(1);
    }

    @Test
    @DisplayName("payload misto e descartado inteiro e produz ERROR sem corpo")
    void payloadMisto_descartaTudo(CapturedOutput log) {
        String payload = payload(
                mudanca(PHONE_NUMBER_ID, PREFIXO_ID + "misto-nosso", PREFIXO_TELEFONE + "03", "SEGREDO-CORPO-E27"),
                mudanca(OUTRO_PHONE_NUMBER_ID, PREFIXO_ID + "misto-outro", PREFIXO_TELEFONE + "04", "terceiro"));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(leads()).isZero();
        assertThat(atendimentos()).isZero();
        assertThat(webhooks()).isZero();
        assertThat(repasses()).isZero();
        assertThat(log.getOut())
                .contains("ERROR")
                .contains("Webhook com destinos mistos descartado")
                .contains(PHONE_NUMBER_ID)
                .contains(OUTRO_PHONE_NUMBER_ID)
                .contains("eventos=2")
                .doesNotContain("SEGREDO-CORPO-E27");
    }

    @Test
    @DisplayName("canal sem phone number id recusa a entrada antes de persistir")
    void semPhoneNumberId_falhaFechado() {
        jdbc.update(
                "UPDATE canal_credencial SET identificador_externo = NULL WHERE id = ?::uuid",
                CREDENCIAL_ID);
        String payload = payload(
                mudanca(PHONE_NUMBER_ID, PREFIXO_ID + "sem-config", PREFIXO_TELEFONE + "05", "nao guardar"));

        assertThat(postar(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leads()).isZero();
        assertThat(atendimentos()).isZero();
        assertThat(webhooks()).isZero();
        assertThat(repasses()).isZero();
    }

    private ResponseEntity<Void> postar(String payload) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", assinatura(payload));
        return http.postForEntity(
                "/webhook/canal", new HttpEntity<>(payload, cabecalhos), Void.class);
    }

    private static String payload(String... mudancas) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"changes\":["
                + String.join(",", mudancas)
                + "]}]}";
    }

    private static String mudanca(String destino, String id, String telefone, String texto) {
        return """
                {"value":{"metadata":{"phone_number_id":"%s"},"contacts":[{"profile":{"name":"Cliente E27"},"wa_id":"%s"}],"messages":[{"from":"%s","id":"%s","timestamp":"1786842000","text":{"body":"%s"},"type":"text"}]}}
                """
                .formatted(destino, telefone, telefone, id, texto)
                .strip();
    }

    private static String mudancaComMensagens(String destino, String contatos, String mensagens) {
        return """
                {"value":{"metadata":{"phone_number_id":"%s"},"contacts":[%s],"messages":[%s]}}
                """
                .formatted(destino, contatos, mensagens)
                .strip();
    }

    private static String mensagem(String telefone, String id, String texto) {
        return ""
                + "{\"from\":\"" + telefone + "\",\"id\":\"" + id
                + "\",\"timestamp\":\"1786842000\",\"text\":{\"body\":\""
                + texto + "\"},\"type\":\"text\"}";
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

    private int leads() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM lead WHERE telefone LIKE ?", Integer.class, PREFIXO_TELEFONE + "%");
    }

    private int atendimentos() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento a JOIN lead l ON l.id = a.lead_id"
                        + " WHERE l.telefone LIKE ?",
                Integer.class,
                PREFIXO_TELEFONE + "%");
    }

    private int webhooks() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE id_externo LIKE ?",
                Integer.class,
                PREFIXO_ID + "%");
    }

    private int repasses() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE tipo = 'automacao.webhook.repassar'"
                        + " AND payload->>'payloadCru' LIKE ?",
                Integer.class,
                "%" + PREFIXO_ID + "%");
    }

    private void limpar() {
        jdbc.update(
                "DELETE FROM outbox_evento WHERE tipo = 'automacao.webhook.repassar'"
                        + " AND payload->>'payloadCru' LIKE ?",
                "%" + PREFIXO_ID + "%");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE ?", PREFIXO_ID + "%");
        jdbc.update(
                "DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE ?",
                PREFIXO_ID + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone LIKE ?)",
                PREFIXO_TELEFONE + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE telefone LIKE ?)",
                PREFIXO_TELEFONE + "%");
        jdbc.update("DELETE FROM lead WHERE telefone LIKE ?", PREFIXO_TELEFONE + "%");
    }

    private int mensagensDoTelefone(String telefone) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone = ?",
                Integer.class,
                telefone);
    }

}
