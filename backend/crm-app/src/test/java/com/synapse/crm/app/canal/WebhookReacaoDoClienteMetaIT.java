package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.evento.ReacaoDoClienteParaTempoReal;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

/**
 * E214 — reacao do cliente pela Meta, do POST assinado ate a leitura do historico.
 *
 * <p>Ana e dona do lead que reage; Bruno e dono de outro lead. A reacao so pode pousar em mensagem da
 * conversa de quem reagiu, sem apagar a reacao que a Ana (usuaria do CRM) ja tinha feito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@RecordApplicationEvents
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-secret=" + WebhookReacaoDoClienteMetaIT.APP_SECRET,
            "synapse.canal.whatsapp.webhook-verify-token=verify-e214",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class WebhookReacaoDoClienteMetaIT extends PostgresIT {

    static final String APP_SECRET = "segredo-e214-reacao-do-cliente";
    private static final String PHONE_NUMBER_ID = "999999999999999";
    private static final String PREFIXO = "wamid.E214-";
    private static final String TELEFONE_ANA_CLIENTE = "5561987652140";
    private static final String TELEFONE_BRUNO_CLIENTE = "5561987652141";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProcessadorDeWebhookEntrada processador;
    @Autowired private ApplicationEvents eventos;

    private UUID anaId;
    private UUID leadDaAna;
    private UUID atendimentoDaAna;
    private UUID mensagemDaAna;
    private UUID leadDoBruno;

    @BeforeEach
    void preparar() {
        limpar();
        anaId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        UUID brunoId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_BRUNO);
        leadDaAna = UUID.randomUUID();
        atendimentoDaAna = UUID.randomUUID();
        mensagemDaAna = conversa(leadDaAna, atendimentoDaAna, anaId, TELEFONE_ANA_CLIENTE, "ana");
        leadDoBruno = UUID.randomUUID();
        conversa(leadDoBruno, UUID.randomUUID(), brunoId, TELEFONE_BRUNO_CLIENTE, "bruno");
        // A reacao da Ana (usuaria do CRM) ja existe e precisa sobreviver a do cliente.
        jdbc.update(
                "INSERT INTO mensagem_reacao (mensagem_id, mensagem_enviada_em, usuario_id, emoji)"
                        + " SELECT id, enviado_em, ?, '👍' FROM mensagem WHERE id = ?",
                anaId, mensagemDaAna);
    }

    @AfterEach
    void restaurar() {
        limpar();
    }

    @Test
    @DisplayName("reação vale só na conversa de quem reagiu, preserva a do CRM e chega à tela por evento")
    void reacaoDoCliente_ciclo_autorizacao_e_lote() throws Exception {
        String lote = payload(
                reacao("r1", TELEFONE_ANA_CLIENTE, "ana", "❤️", 1758370000L) + ","
                        // alvo de outra conversa: o id existe, mas a mensagem e do lead do Bruno
                        + reacao("r2", TELEFONE_ANA_CLIENTE, "bruno", "😂", 1758370001L) + ","
                        + reacao("r3", TELEFONE_ANA_CLIENTE, "desconhecida", "😮", 1758370002L) + ","
                        + """
                        {"from":"%s","id":"%stexto","timestamp":"1758370003","type":"text","text":{"body":"obrigado"}}
                        """.formatted(TELEFONE_ANA_CLIENTE, PREFIXO));

        assertThat(postarComAssinatura(lote, "sha256=" + "0".repeat(64)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postar(lote).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(reacaoDoClienteGravada(mensagemDaAna)).isEqualTo("❤️");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mensagem_reacao_cliente r JOIN mensagem m ON m.id = r.mensagem_id"
                        + " JOIN atendimento a ON a.id = m.atendimento_id WHERE a.lead_id = ?",
                Integer.class, leadDoBruno)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT descartes::text FROM webhook_entrada WHERE id_externo = ?", String.class, PREFIXO + "r1"))
                .contains("ALVO_DESCONHECIDO")
                .doesNotContain(TELEFONE_ANA_CLIENTE);
        assertThat(jdbc.queryForObject(
                "SELECT itens_descartados FROM webhook_entrada WHERE id_externo = ?", Integer.class, PREFIXO + "r1"))
                .isEqualTo(2);
        assertThat(eventos.stream(ReacaoDoClienteParaTempoReal.class))
                .singleElement()
                .satisfies(evento -> {
                    assertThat(evento.mensagemId()).isEqualTo(mensagemDaAna);
                    assertThat(evento.atendimentoId()).isEqualTo(atendimentoDaAna);
                    assertThat(evento.emoji()).isEqualTo("❤️");
                });

        // Reacao nao e mensagem: so o texto do lote entra no historico, e a reacao nao cria lead.
        assertThat(jdbc.queryForList(
                "SELECT m.conteudo FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                        + " WHERE a.lead_id = ? AND m.remetente_tipo = 'LEAD'",
                String.class, leadDaAna)).containsExactly("obrigado");

        Map<String, Object> lida = mensagemPelaApi(EMAIL_ANA);
        assertThat(lida.get("reacaoDoCliente")).isEqualTo("❤️");
        assertThat(lida.get("reacoes").toString()).contains("👍");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mensagem_reacao WHERE mensagem_id = ? AND usuario_id = ?",
                Integer.class, mensagemDaAna, anaId)).isEqualTo(1);
        // RN-CRM-01: conhecer a conversa nao basta; Bruno nao le o historico do lead da Ana.
        assertThat(lerHistorico(EMAIL_BRUNO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Reentrega do mesmo POST: nada muda e nada e publicado de novo.
        assertThat(postar(lote).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();
        assertThat(eventos.stream(ReacaoDoClienteParaTempoReal.class)).hasSize(1);

        // Substituicao, depois evento atrasado (mais antigo) que nao pode voltar o emoji.
        postarEProcessar(reacao("r4", TELEFONE_ANA_CLIENTE, "ana", "😮", 1758370100L));
        assertThat(reacaoDoClienteGravada(mensagemDaAna)).isEqualTo("😮");
        postarEProcessar(reacao("r5", TELEFONE_ANA_CLIENTE, "ana", "👎", 1758370050L));
        assertThat(reacaoDoClienteGravada(mensagemDaAna)).isEqualTo("😮");

        // Remocao: emoji vazio some da tela e a do CRM continua.
        postarEProcessar(reacao("r6", TELEFONE_ANA_CLIENTE, "ana", "", 1758370200L));
        Map<String, Object> removida = mensagemPelaApi(EMAIL_ANA);
        assertThat(removida.get("reacaoDoCliente")).isNull();
        assertThat(removida.get("reacoes").toString()).contains("👍");
        assertThat(eventos.stream(ReacaoDoClienteParaTempoReal.class))
                .extracting(ReacaoDoClienteParaTempoReal::emoji)
                .containsExactly("❤️", "😮", null);
    }

    private UUID conversa(UUID leadId, UUID atendimentoId, UUID donoId, String telefone, String sufixo) {
        UUID mensagemId = UUID.randomUUID();
        Instant enviadaEm = Instant.parse("2026-09-20T12:00:00Z");
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                leadId, "Cliente E214 " + sufixo, telefone, donoId);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                atendimentoId, leadId, donoId);
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo,"
                        + " status_entrega, enviado_em) VALUES (?, ?, 'ATENDENTE', ?, 'TEXTO', 'Posso ajudar?',"
                        + " 'ENTREGUE', ?)",
                mensagemId, atendimentoId, donoId, Timestamp.from(enviadaEm));
        jdbc.update(
                "INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)"
                        + " VALUES (?, ?, ?, ?)",
                PREFIXO + sufixo, mensagemId, Timestamp.from(enviadaEm), atendimentoId);
        return mensagemId;
    }

    private String reacaoDoClienteGravada(UUID mensagemId) {
        return jdbc.queryForObject(
                "SELECT emoji FROM mensagem_reacao_cliente WHERE mensagem_id = ?", String.class, mensagemId);
    }

    private void postarEProcessar(String item) {
        assertThat(postar(payload(item)).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();
    }

    private static String reacao(String id, String de, String alvo, String emoji, long quando) {
        return """
                {"from":"%s","id":"%s%s","timestamp":"%d","type":"reaction",
                 "reaction":{"message_id":"%s%s","emoji":"%s"}}
                """.formatted(de, PREFIXO, id, quando, PREFIXO, alvo, emoji);
    }

    private static String payload(String mensagens) {
        return """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"%s"},
                  "contacts":[{"profile":{"name":"Cliente"},"wa_id":"%s"}],
                  "messages":[%s]
                }}]}]}
                """.formatted(PHONE_NUMBER_ID, TELEFONE_ANA_CLIENTE, mensagens).strip();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> mensagemPelaApi(String email) {
        ResponseEntity<Map> resposta = lerHistorico(email);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) resposta.getBody().get("mensagens");
        return mensagens.stream()
                .filter(mensagem -> mensagemDaAna.toString().equals(mensagem.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> lerHistorico(String email) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        return ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/atendimentos/" + atendimentoDaAna + "/mensagens", Map.class);
    }

    private ResponseEntity<Void> postar(String payload) {
        return postarComAssinatura(payload, assinatura(payload));
    }

    private ResponseEntity<Void> postarComAssinatura(String payload, String assinatura) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", assinatura);
        return http.postForEntity("/webhook/canal", new HttpEntity<>(payload, cabecalhos), Void.class);
    }

    private static String assinatura(String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void limpar() {
        jdbc.update("DELETE FROM outbox_evento WHERE tipo = 'automacao.webhook.repassar'"
                + " AND payload->>'payloadCru' LIKE ?", "%" + PREFIXO + "%");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE ?", PREFIXO + "%");
        for (String telefone : List.of(TELEFONE_ANA_CLIENTE, TELEFONE_BRUNO_CLIENTE)) {
            jdbc.update(
                    "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a"
                            + " JOIN lead l ON l.id = a.lead_id WHERE l.telefone = ?)",
                    telefone);
            jdbc.update(
                    "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE telefone = ?)", telefone);
            jdbc.update("DELETE FROM lead WHERE telefone = ?", telefone);
        }
    }
}
