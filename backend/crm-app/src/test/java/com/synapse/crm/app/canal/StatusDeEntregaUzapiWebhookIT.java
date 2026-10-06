package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Webhook HTTP autenticado, Postgres, historico e STOMP reais; nenhuma chamada ao provedor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "synapse.canal.whatsapp.provedor=uzapi-autotic",
    "synapse.canal.whatsapp.url-base=http://127.0.0.1:1",
    "synapse.canal.whatsapp.numero-principal=phone-status-uzapi",
    "synapse.canal.whatsapp.token=token-de-teste",
    "synapse.canal.whatsapp.versao-api=v1",
    "synapse.canal.whatsapp.webhook-secret=segredo-status-uzapi",
    "synapse.canal.foto-perfil.habilitado=false",
    "synapse.automacao.repasse-webhook.url=http://127.0.0.1:1/nao-sera-chamado",
    "synapse.automacao.repasse-webhook.intervalo-ms=3600000"
})
class StatusDeEntregaUzapiWebhookIT extends PostgresIT {
    private static final String DESTINO = "phone-status-uzapi";
    private static final String SEGREDO = "segredo-status-uzapi";
    private static final Instant ENVIADO_EM = Instant.parse("2026-10-01T15:00:00Z");
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SimpUserRegistry usuarios;
    @Value("${local.server.port}") private int porta;
    private UUID canalId;
    private UUID credencialId;
    private UUID leadId;
    private UUID atendimentoId;
    private UUID mensagemId;
    private UUID outroLead;
    private UUID outroAtendimento;
    private UUID outraMensagem;
    private String wamid;

    @BeforeEach
    void preparar() {
        canalId = UUID.randomUUID();
        credencialId = UUID.randomUUID();
        leadId = UUID.randomUUID();
        atendimentoId = UUID.randomUUID();
        mensagemId = UUID.randomUUID();
        outroLead = UUID.randomUUID();
        outroAtendimento = UUID.randomUUID();
        outraMensagem = UUID.randomUUID();
        wamid = "wamid.STATUS-UZAPI-" + mensagemId;
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, 'Status Uzapi teste', 'WHATSAPP')", canalId);
        jdbc.update("INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo)"
                + " VALUES (?, ?, '5543900000000', ?, 'token-teste', true)", credencialId, canalId, DESTINO);
        criarMensagem(leadId, atendimentoId, mensagemId, wamid);
        criarMensagem(outroLead, outroAtendimento, outraMensagem, "wamid.STATUS-UZAPI-" + outraMensagem);
    }

    private void criarMensagem(UUID lead, UUID atendimento, UUID mensagem, String externo) {
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'", UUID.class);
        jdbc.update("INSERT INTO lead (id, nome, status_basico, atendente_responsavel_id)"
                + " VALUES (?, 'Teste status Uzapi', 'EM_ATENDIMENTO', ?)", lead, ana);
        jdbc.update("INSERT INTO atendimento (id, lead_id, status, iniciado_em, atendente_id)"
                + " VALUES (?, ?, 'EM_ATENDIMENTO', ?, ?)", atendimento, lead, Timestamp.from(ENVIADO_EM), ana);
        jdbc.update("INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, status_entrega, enviado_em)"
                + " VALUES (?, ?, 'ATENDENTE', ?, 'TEXTO', 'Mensagem sintetica', 'ENVIADO', ?)",
                mensagem, atendimento, ana, Timestamp.from(ENVIADO_EM));
        jdbc.update("INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)"
                + " VALUES (?, ?, ?, ?)", externo, mensagem, Timestamp.from(ENVIADO_EM), atendimento);
    }

    @AfterEach
    void limpar() {
        for (UUID atendimento : List.of(atendimentoId, outroAtendimento)) {
            jdbc.update("DELETE FROM mensagem_id_externo WHERE atendimento_id=?", atendimento);
            jdbc.update("DELETE FROM mensagem WHERE atendimento_id=?", atendimento);
            jdbc.update("DELETE FROM atendimento WHERE id=?", atendimento);
        }
        jdbc.update("DELETE FROM lead WHERE id IN (?, ?)", leadId, outroLead);
        jdbc.update("DELETE FROM canal_credencial WHERE id=?", credencialId);
        jdbc.update("DELETE FROM canal WHERE id=?", canalId);
    }

    @Test
    void idNativoComWamidAvancaEntregaLeituraEPersisteNoHistorico() {
        postar("3EB0NATIVO", wamid, "sent", SEGREDO, DESTINO);
        assertThat(status(mensagemId)).isEqualTo("ENVIADO");
        postar("3EB0NATIVO", wamid, "delivered", SEGREDO, DESTINO);
        assertThat(status(mensagemId)).isEqualTo("ENTREGUE");
        postar("3EB0NATIVO", wamid, "read", SEGREDO, DESTINO);
        postar("3EB0NATIVO", wamid, "delivered", SEGREDO, DESTINO);
        assertThat(status(mensagemId)).isEqualTo("LIDO");
        assertThat(status(outraMensagem)).isEqualTo("ENVIADO");
        String token = ApoioAutenticacao.login(http, "ana@dev.local", ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        assertThat(historico(token).get("statusEntrega")).isEqualTo("LIDO");
        assertThat(historico(token).get("statusEntrega")).isEqualTo("LIDO");
    }

    @Test
    void segredoErradoEDestinoAlheioNaoAlteramMensagens() {
        assertThat(postar("3EB0NATIVO", wamid, "read", "errado", DESTINO)).isEqualTo(HttpStatus.FORBIDDEN);
        postar("3EB0NATIVO", wamid, "read", SEGREDO, "destino-alheio");
        assertThat(status(mensagemId)).isEqualTo("ENVIADO");
        assertThat(status(outraMensagem)).isEqualTo("ENVIADO");
    }

    @Test
    void desconhecidosEConversationGenericaNaoGeramConfirmacaoFalsa() {
        postar("nativo-desconhecido", "conversa-generica", "read", SEGREDO, DESTINO);
        postar("nativo-desconhecido", "wamid.nao-cadastrado", "delivered", SEGREDO, DESTINO);
        postar("3EB0NATIVO", wamid, "played", SEGREDO, DESTINO);
        postar("3EB0NATIVO", wamid, "desconhecido", SEGREDO, DESTINO);
        assertThat(status(mensagemId)).isEqualTo("ENVIADO");
    }

    @Test
    void idPrincipalConhecidoTemPrioridadeMesmoQuandoDuplicado() {
        jdbc.update("UPDATE mensagem_id_externo SET wamid='NATIVO-CONHECIDO' WHERE mensagem_id=?", mensagemId);
        String outroWamid = "wamid.STATUS-UZAPI-" + outraMensagem;
        postar("NATIVO-CONHECIDO", outroWamid, "read", SEGREDO, DESTINO);
        postar("NATIVO-CONHECIDO", outroWamid, "read", SEGREDO, DESTINO);
        assertThat(status(mensagemId)).isEqualTo("LIDO");
        assertThat(status(outraMensagem)).isEqualTo("ENVIADO");
    }

    @Test
    void falhaPersisteCodigoEMotivoSemAceiteTardioRebaixar() {
        postar("3EB0NATIVO", wamid, "failed", SEGREDO, DESTINO);
        postar("3EB0NATIVO", wamid, "sent", SEGREDO, DESTINO);
        assertThat(status(mensagemId)).isEqualTo("FALHOU");
        String token = ApoioAutenticacao.login(http, "ana@dev.local", ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        assertThat(historico(token).get("erroEntrega").toString()).contains("400", "Recusada");
    }

    @Test
    void falhaSemTitlePersisteDetalheDoErroNoHistorico() {
        String corpo = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"field":"messages","value":{
                 "metadata":{"phone_number_id":"%s"},"statuses":[{"id":"%s","status":"failed",
                 "conversation":{"id":"%s"},"errors":[{"code":499,"message":"Resumo genérico",
                 "error_data":{"details":"Motivo detalhado"}}]}]}}]}]}
                """.formatted(DESTINO, "3EB0NATIVO", wamid);

        assertThat(postarPayload(corpo, SEGREDO)).isEqualTo(HttpStatus.OK);
        assertThat(status(mensagemId)).isEqualTo("FALHOU");
        String token = ApoioAutenticacao.login(http, "ana@dev.local", ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        assertThat(historico(token).get("erroEntrega").toString()).contains("Motivo detalhado");
    }

    @Test
    void webhookPublicaStatusNoStompDepoisDePersistirESemDuplicar() throws Exception {
        String token = ApoioAutenticacao.login(http, "ana@dev.local", ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        var stomp = new WebSocketStompClient(new StandardWebSocketClient());
        StompSession sessao = null;
        try {
            sessao = stomp.connectAsync("ws://localhost:" + porta + "/ws?access_token=" + token,
                    new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
            var recebidas = new LinkedBlockingQueue<String>();
            String destino = "/user/queue/atendimento." + atendimentoId;
            sessao.subscribe(destino, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) {
                    recebidas.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                }
            });
            await().atMost(Duration.ofSeconds(5)).until(() -> !usuarios.findSubscriptions(
                    assinatura -> destino.equals(assinatura.getDestination())).isEmpty());
            postar("3EB0NATIVO", wamid, "delivered", SEGREDO, DESTINO);
            String evento = recebidas.poll(5, TimeUnit.SECONDS);
            assertThat(evento).contains("STATUS", "ENTREGUE", mensagemId.toString(), atendimentoId.toString());
            assertThat(status(mensagemId)).isEqualTo("ENTREGUE");
            postar("3EB0NATIVO", wamid, "delivered", SEGREDO, DESTINO);
            assertThat(recebidas.poll(1, TimeUnit.SECONDS)).isNull();
        } finally {
            if (sessao != null && sessao.isConnected()) sessao.disconnect();
            stomp.stop();
        }
    }

    @Test
    void historicoContinuaNegandoLeadDeOutroAtendente() {
        String token = ApoioAutenticacao.login(http, "bruno@dev.local", ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        var resposta = ApoioAutenticacao.comToken(http, token, HttpMethod.GET,
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens", String.class);
        assertThat(resposta.getStatusCode().value()).isIn(403, 404);
        assertThat(resposta.getBody()).doesNotContain("Mensagem sintetica");
    }

    private String status(UUID mensagem) {
        return jdbc.queryForObject("SELECT status_entrega FROM mensagem WHERE id=?", String.class, mensagem);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> historico(String token) {
        var resposta = ApoioAutenticacao.comToken(http, token, HttpMethod.GET,
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens", Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> mensagens = (List<Map<String, Object>>) resposta.getBody().get("mensagens");
        assertThat(mensagens).hasSize(1);
        return mensagens.get(0);
    }

    private HttpStatus postar(String id, String alternativo, String estado, String segredo, String destino) {
        String corpo = """
                {"object":"whatsapp_business_account","entry":[{"changes":[{"field":"messages","value":{
                 "metadata":{"phone_number_id":"%s"},"statuses":[{"id":"%s","status":"%s",
                 "conversation":{"id":"%s"},"errors":[{"code":400,"title":"Recusada"}]}]}}]}]}
                """.formatted(destino, id, estado, alternativo);
        return postarPayload(corpo, segredo);
    }

    private HttpStatus postarPayload(String corpo, String segredo) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return HttpStatus.valueOf(http.postForEntity("/webhook/canal?secret=" + segredo,
                new HttpEntity<>(corpo, cabecalhos), Void.class).getStatusCode().value());
    }
}
