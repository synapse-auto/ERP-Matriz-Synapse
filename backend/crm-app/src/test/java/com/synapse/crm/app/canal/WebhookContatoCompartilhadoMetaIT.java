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
 * Contato compartilhado pela Meta, do POST assinado ate a leitura do historico pela API.
 *
 * <p>Passa pelo ponto de entrada real: {@code WebhookCanalController} (HMAC, destino, fila) e o
 * job de entrada chamado como o {@code @Scheduled} chama. Os payloads seguem a referencia oficial
 * de {@code messages/contacts}, com nomes e numeros sinteticos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-secret=" + WebhookContatoCompartilhadoMetaIT.APP_SECRET,
            "synapse.canal.whatsapp.webhook-verify-token=verify-contato",
            "synapse.canal.foto-perfil.habilitado=false",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class WebhookContatoCompartilhadoMetaIT extends PostgresIT {

    static final String APP_SECRET = "segredo-contato-meta-para-assinatura";
    private static final String PHONE_NUMBER_ID = "phone-number-id-contato-meta";
    private static final String TELEFONE_CLIENTE = "5561977770001";
    private static final String SENHA_GESTOR =
            "$2a$10$5vISVeL7I/o7K8rKLvXFDOko5iYacVlYlvxIJqTywAoLzf2eP6dPK";
    private static final String SENHA_ATENDENTE =
            "$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProcessadorDeWebhookEntrada processador;

    private UUID canalId;
    private UUID credencialId;
    private UUID leadId;

    @BeforeEach
    void preparar() {
        canalId = UUID.randomUUID();
        credencialId = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canalId, "Canal Contato Meta");
        jdbc.update(
                "INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                credencialId, canalId, "5561900000000", PHONE_NUMBER_ID, "token-qualquer", true);
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo) VALUES (?, 'IA', 'ia@local',"
                        + " 'xxx', 'ATENDENTE', true) ON CONFLICT DO NOTHING",
                UUID.fromString("56c5270c-2d0f-4889-8d76-e8ebde78ecaa"));
        leadId = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, telefone, nome) VALUES (?, ?, ?)", leadId, TELEFONE_CLIENTE, "Cliente");
    }

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM mensagem_id_externo WHERE mensagem_id IN (SELECT m.id FROM mensagem m"
                        + " JOIN atendimento a ON a.id = m.atendimento_id WHERE a.lead_id = ?)",
                leadId);
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", leadId);
        jdbc.update("DELETE FROM atendimento WHERE lead_id = ?", leadId);
        jdbc.update("DELETE FROM lead WHERE id = ?", leadId);
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE 'wamid.contato-meta.%'");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE 'wamid.contato-meta.%'");
        jdbc.update("DELETE FROM canal_credencial WHERE id = ?", credencialId);
        jdbc.update("DELETE FROM canal WHERE id = ?", canalId);
        jdbc.update("DELETE FROM usuario WHERE email LIKE 'contato-meta-%@teste.local'");
    }

    @Test
    void contatoDoPostMistoChegaAoHistoricoSemDuplicarEOItemRuimFicaRegistrado() {
        String misto = payload("""
                {"from":"%1$s","id":"wamid.contato-meta.cartao","timestamp":"1720000000","type":"contacts",
                 "contacts":[
                   {"name":{"formatted_name":"Arquiteta Exemplo"},
                    "phones":[{"phone":"+55 61 3333-0000","type":"WORK"},
                              {"phone":"+55 61 98888-0000","wa_id":"5561988880000","type":"CELL"}]},
                   {"name":{"formatted_name":"Sem Telefone"}}]},
                {"from":"%1$s","id":"wamid.contato-meta.quebrado","timestamp":9223372036854775807,
                 "type":"text","text":{"body":"quebrado"}},
                {"from":"%1$s","id":"wamid.contato-meta.texto","timestamp":"1720000001","type":"text",
                 "text":{"body":"segue o contato"}}
                """.formatted(TELEFONE_CLIENTE));

        assertThat(postar(misto).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Reentrega do mesmo POST: deduplicada pela chave da linha.
        assertThat(postar(misto).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Outro POST repetindo o cartao depois de um item novo: chave de linha diferente, mas o
        // cartao ja tem id externo registrado e nao pode virar segunda mensagem.
        postar(payload("""
                {"from":"%1$s","id":"wamid.contato-meta.outro","timestamp":"1720000002","type":"text",
                 "text":{"body":"de novo"}},
                {"from":"%1$s","id":"wamid.contato-meta.cartao","timestamp":"1720000000","type":"contacts",
                 "contacts":[{"name":{"formatted_name":"Arquiteta Exemplo"}}]}
                """.formatted(TELEFONE_CLIENTE)));

        processador.processarPendentes();

        assertThat(tiposDoLead()).containsExactlyInAnyOrder("CONTATO", "TEXTO", "TEXTO");

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT processado_em, itens_descartados, descartes::text AS descartes FROM webhook_entrada"
                        + " WHERE id_externo = 'wamid.contato-meta.cartao'");
        assertThat(linha.get("processado_em")).isNotNull();
        assertThat(((Number) linha.get("itens_descartados")).intValue()).isEqualTo(1);
        assertThat((String) linha.get("descartes"))
                .contains("ITEM_MALFORMADO")
                .contains("\"text\"")
                // Registro operacional sem dado pessoal nem conteudo.
                .doesNotContain(TELEFONE_CLIENTE)
                .doesNotContain("Arquiteta")
                .doesNotContain("quebrado");

        UUID atendimentoId = jdbc.queryForObject(
                "SELECT id FROM atendimento WHERE lead_id = ?", UUID.class, leadId);
        String gestor = login("contato-meta-gestor@teste.local", SENHA_GESTOR, "GESTOR", "gestor123");

        // Duas leituras seguidas: o que a tela recebe apos F5 e o que esta no banco, nao estado local.
        for (int leitura = 0; leitura < 2; leitura++) {
            Map<String, Object> contato = mensagemDoTipo(gestor, atendimentoId, "CONTATO");
            assertThat(contato.get("remetenteTipo")).isEqualTo("LEAD");
            assertThat(contato.get("conteudo")).isNull();
            assertThat(contato.get("midiaMetadados").toString())
                    .contains("Arquiteta Exemplo")
                    .contains("+55 61 3333-0000")
                    .contains("+55 61 98888-0000")
                    .contains("5561988880000")
                    .contains("Sem Telefone");
        }
    }

    @Test
    void atendenteDeOutroLeadNaoAlcancaOContato() {
        postar(payload("""
                {"from":"%s","id":"wamid.contato-meta.visibilidade","timestamp":"1720000000","type":"contacts",
                 "contacts":[{"name":{"formatted_name":"Contato Restrito"},"phones":[{"phone":"+55 61 3000-0000"}]}]}
                """.formatted(TELEFONE_CLIENTE)));
        processador.processarPendentes();

        UUID atendimentoId = jdbc.queryForObject(
                "SELECT id FROM atendimento WHERE lead_id = ?", UUID.class, leadId);
        UUID donoId = UUID.randomUUID();
        inserirUsuario(donoId, "contato-meta-dono@teste.local", SENHA_ATENDENTE, "ATENDENTE");
        inserirUsuario(UUID.randomUUID(), "contato-meta-outro@teste.local", SENHA_ATENDENTE, "ATENDENTE");
        jdbc.update(
                "UPDATE atendimento SET status = 'EM_ATENDIMENTO'::status_atendimento, atendente_id = ? WHERE id = ?",
                donoId, atendimentoId);
        jdbc.update(
                "UPDATE lead SET status_basico = 'EM_ATENDIMENTO'::status_basico_lead,"
                        + " atendente_responsavel_id = ? WHERE id = ?",
                donoId, leadId);

        String dono = ApoioAutenticacao.login(http, "contato-meta-dono@teste.local", "atendente123").accessToken();
        String outro = ApoioAutenticacao.login(http, "contato-meta-outro@teste.local", "atendente123").accessToken();

        assertThat(historico(dono, atendimentoId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historico(outro, atendimentoId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void semCartaoOuSoStatusNaoCriaContatoNemDescarteETipoNovoDeixaEvidencia() {
        // value.contacts[] do envelope esta presente em todo POST de mensagem; sozinho, e remetente.
        postar(payload("""
                {"from":"%s","id":"wamid.contato-meta.so-texto","timestamp":"1720000000","type":"text",
                 "text":{"body":"ola"}}
                """.formatted(TELEFONE_CLIENTE)));
        String soStatus = """
                {"object":"whatsapp_business_account","entry":[{"id":"waba","changes":[{"value":{
                  "metadata":{"phone_number_id":"%s"},
                  "statuses":[{"id":"wamid.contato-meta.status","status":"delivered","recipient_id":"%s"}]
                },"field":"messages"}]}]}
                """.formatted(PHONE_NUMBER_ID, TELEFONE_CLIENTE);
        assertThat(postar(soStatus).getStatusCode()).isEqualTo(HttpStatus.OK);
        postar(payload("""
                {"from":"%s","id":"wamid.contato-meta.reacao","timestamp":"1720000000","type":"reaction",
                 "reaction":{"message_id":"wamid.qualquer","emoji":"ok"}}
                """.formatted(TELEFONE_CLIENTE)));

        processador.processarPendentes();

        assertThat(tiposDoLead()).containsExactly("TEXTO");
        assertThat(jdbc.queryForObject(
                "SELECT itens_descartados FROM webhook_entrada WHERE id_externo = 'wamid.contato-meta.so-texto'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT descartes::text FROM webhook_entrada WHERE id_externo = 'wamid.contato-meta.so-texto'",
                String.class)).isNull();
        // POST so de status nem entra na fila de mensagens: nao ha linha para acusar descarte.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE id_externo = 'wamid.contato-meta.status'",
                Integer.class)).isZero();
        // Tipo documentado mas nao traduzido: localizavel pela linha, sem abrir o payload.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE itens_descartados > 0"
                        + " AND descartes @> '[{\"tipo\":\"reaction\",\"motivo\":\"TIPO_NAO_SUPORTADO\"}]'"
                        + " AND id_externo = 'wamid.contato-meta.reacao'",
                Integer.class)).isEqualTo(1);
    }

    private List<String> tiposDoLead() {
        return jdbc.queryForList(
                "SELECT m.tipo::text FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                        + " WHERE a.lead_id = ?",
                String.class, leadId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mensagemDoTipo(String token, UUID atendimentoId, String tipo) {
        ResponseEntity<Map> resposta = historico(token, atendimentoId);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) resposta.getBody().get("mensagens");
        return mensagens.stream()
                .filter(mensagem -> tipo.equals(mensagem.get("tipo")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("historico sem mensagem " + tipo));
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> historico(String token, UUID atendimentoId) {
        return ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/atendimentos/" + atendimentoId + "/mensagens", Map.class);
    }

    private String login(String email, String hash, String papel, String senha) {
        inserirUsuario(UUID.randomUUID(), email, hash, papel);
        return ApoioAutenticacao.login(http, email, senha).accessToken();
    }

    private void inserirUsuario(UUID id, String email, String hash, String papel) {
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo, senha_alterada_em)"
                        + " VALUES (?, ?, ?, ?, ?::papel_usuario, true, now()) ON CONFLICT DO NOTHING",
                id, email, email, hash, papel);
    }

    private String payload(String mensagens) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"waba","changes":[{"value":{
                  "messaging_product":"whatsapp",
                  "metadata":{"display_phone_number":"5561900000000","phone_number_id":"%s"},
                  "contacts":[{"wa_id":"%s","profile":{"name":"Cliente"}}],
                  "messages":[%s]
                },"field":"messages"}]}]}
                """.formatted(PHONE_NUMBER_ID, TELEFONE_CLIENTE, mensagens);
    }

    private ResponseEntity<String> postar(String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", assinatura(payload));
        return http.postForEntity("/webhook/canal", new HttpEntity<>(payload, headers), String.class);
    }

    private static String assinatura(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
