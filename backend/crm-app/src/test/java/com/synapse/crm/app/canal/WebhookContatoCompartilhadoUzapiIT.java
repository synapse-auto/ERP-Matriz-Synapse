package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * Contato compartilhado pela Uzapi/Autotic, do POST com {@code ?secret} ate a leitura do historico.
 *
 * <p>O corpo segue o schema {@code ContactsMessage} de {@code /webhook/message/contacts} do Swagger
 * oficial, com nomes e numeros sinteticos. O provedor aponta para uma porta fechada: nada aqui
 * pode depender de chamada de saida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=uzapi-autotic",
            "synapse.canal.whatsapp.url-base=http://127.0.0.1:1",
            "synapse.canal.whatsapp.numero-principal=phone-id-contato-uzapi",
            "synapse.canal.whatsapp.token=token-de-teste",
            "synapse.canal.whatsapp.versao-api=v1",
            "synapse.canal.whatsapp.webhook-secret=" + WebhookContatoCompartilhadoUzapiIT.SEGREDO,
            "synapse.canal.foto-perfil.habilitado=false",
            "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
        })
class WebhookContatoCompartilhadoUzapiIT extends PostgresIT {

    static final String SEGREDO = "segredo-contato-uzapi";
    private static final String PHONE_NUMBER_ID = "phone-id-contato-uzapi";
    private static final String TELEFONE_CLIENTE = "5561977770002";

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
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canalId, "Canal Contato Uzapi");
        jdbc.update(
                "INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                credencialId, canalId, "5543900000000", PHONE_NUMBER_ID, "token-qualquer", true);
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
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE 'CONTATO-UZAPI-%'");
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE 'CONTATO-UZAPI-%'");
        jdbc.update("DELETE FROM canal_credencial WHERE id = ?", credencialId);
        jdbc.update("DELETE FROM canal WHERE id = ?", canalId);
        jdbc.update("DELETE FROM usuario WHERE email = 'contato-uzapi-gestor@teste.local'");
    }

    @Test
    void contatoCompartilhadoChegaAoHistoricoEOSegredoErradoNaoGravaNada() {
        String cartao = payload("""
                {"from":"%s","id":"CONTATO-UZAPI-1","isGroup":false,"timestamp":"1768843447","type":"contacts",
                 "contacts":[{"name":{"first_name":"Loja","formatted_name":"Loja Exemplo"},
                              "phones":[{"phone":"+55 43 3300-0000","type":"Comercial"},
                                        {"phone":"+55 43 99900-0000","type":"Celular"}]}]}
                """.formatted(TELEFONE_CLIENTE));

        assertThat(postar(cartao, "segredo-errado").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE id_externo = 'CONTATO-UZAPI-1'", Integer.class))
                .isZero();

        assertThat(postar(cartao, SEGREDO).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postar(cartao, SEGREDO).getStatusCode()).isEqualTo(HttpStatus.OK);
        processador.processarPendentes();

        assertThat(jdbc.queryForList(
                "SELECT m.tipo::text FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                        + " WHERE a.lead_id = ?",
                String.class, leadId)).containsExactly("CONTATO");
        assertThat(jdbc.queryForObject(
                "SELECT itens_descartados FROM webhook_entrada WHERE id_externo = 'CONTATO-UZAPI-1'",
                Integer.class)).isZero();

        UUID atendimentoId = jdbc.queryForObject(
                "SELECT id FROM atendimento WHERE lead_id = ?", UUID.class, leadId);
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo, senha_alterada_em)"
                        + " VALUES (?, 'Gestor', 'contato-uzapi-gestor@teste.local',"
                        + " '$2a$10$5vISVeL7I/o7K8rKLvXFDOko5iYacVlYlvxIJqTywAoLzf2eP6dPK', 'GESTOR', true, now())"
                        + " ON CONFLICT DO NOTHING",
                UUID.randomUUID());
        String gestor = ApoioAutenticacao.login(http, "contato-uzapi-gestor@teste.local", "gestor123").accessToken();

        Map<String, Object> contato = unicaMensagem(gestor, atendimentoId);
        assertThat(contato.get("tipo")).isEqualTo("CONTATO");
        assertThat(contato.get("midiaMetadados").toString())
                .contains("Loja Exemplo")
                .contains("+55 43 3300-0000")
                .contains("+55 43 99900-0000");
    }

    @Test
    void storyComCartaoNaoViraMensagemNemDescarte() {
        postar(payload("""
                {"from":"%s","id":"CONTATO-UZAPI-STORY","group_id":"status@broadcast","timestamp":"1768843447",
                 "type":"contacts","contacts":[{"name":{"formatted_name":"Nao Deve Aparecer"}}]}
                """.formatted(TELEFONE_CLIENTE)), SEGREDO);
        processador.processarPendentes();

        // Status/Story nem entra na fila: idsExternos ja o filtra.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM webhook_entrada WHERE id_externo = 'CONTATO-UZAPI-STORY'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id WHERE a.lead_id = ?",
                Integer.class, leadId)).isZero();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unicaMensagem(String token, UUID atendimentoId) {
        ResponseEntity<Map> resposta = ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/atendimentos/" + atendimentoId + "/mensagens", Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) resposta.getBody().get("mensagens");
        assertThat(mensagens).hasSize(1);
        return mensagens.get(0);
    }

    private String payload(String mensagens) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"","changes":[{"value":{
                  "messaging_product":"whatsapp",
                  "metadata":{"display_phone_number":"5543900000000","phone_number_id":"%s"},
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
