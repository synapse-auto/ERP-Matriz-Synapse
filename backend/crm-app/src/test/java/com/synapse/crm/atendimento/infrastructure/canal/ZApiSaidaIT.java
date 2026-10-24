package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

/** API real -> outbox -> job agendado -> adaptador real -> HTTP simulado -> Postgres real. */
@SpringBootTest(classes = SynapseCrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(ZApiSaidaIT.TransporteDeTeste.class)
@TestPropertySource(properties = {
        "synapse.canal.whatsapp.provedor=z-api", "synapse.canal.whatsapp.numero-principal=instancia-teste",
        "synapse.canal.z-api.habilitado=false", "synapse.canal.outbox.intervalo-ms=3600000",
        "synapse.canal.webhook.intervalo-ms=3600000"})
class ZApiSaidaIT extends PostgresIT {
    private static final UUID CREDENCIAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PREFIXO = "ZAPI-SAIDA-";
    private static final String URL = "https://api.z-api.io/instances/instancia-teste/token/segredo-teste/send-text";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate http;
    @Autowired private PublicadorDaOutbox publicador;
    @Autowired private Transporte transporte;
    private UUID lead;
    private UUID atendimento;
    private HttpHeaders headers;

    record Transporte(MockRestServiceServer servidor, ZApiAdapter adapter) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TransporteDeTeste {
        @Bean Transporte transporte(ObjectMapper json, CircuitBreakerRegistry breakers) {
            RestClient.Builder cliente = RestClient.builder();
            MockRestServiceServer servidor = MockRestServiceServer.bindTo(cliente).build();
            return new Transporte(servidor, new ZApiAdapter(cliente,
                    new ZApiProperties(true, "instancia-teste", "segredo-teste", "cliente-teste", CREDENCIAL,
                            Duration.ofSeconds(10), 4096, 1), json, breakers));
        }
        @Bean ZApiAdapter saidaZApi(Transporte transporte) { return transporte.adapter(); }
        @Bean TradutorDeCanal entradaInexistenteSomenteNoTeste() {
            // Apenas permite subir o teste de saida. Nega toda assinatura; NAO e entrada implementada.
            TradutorDeCanal negado = mock(TradutorDeCanal.class);
            when(negado.provedor()).thenReturn("z-api");
            return negado;
        }
    }

    @BeforeEach
    void preparar() {
        limpar();
        transporte.servidor().reset();
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'ana@dev.local'", UUID.class);
        UUID canal = UUID.randomUUID();
        lead = UUID.randomUUID();
        atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canal, PREFIXO + canal);
        jdbc.update("INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo) VALUES (?, ?, ?, ?, ?, true)",
                CREDENCIAL, canal, "5561999999999", "instancia-teste", "secret://teste/zapi");
        jdbc.update("INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO', now(), now())",
                lead, PREFIXO + lead, "5561999999999", ana);
        jdbc.update("INSERT INTO atendimento (id, lead_id, canal_credencial_id, atendente_id, status) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                atendimento, lead, CREDENCIAL, ana);
        headers = new HttpHeaders();
        headers.setBearerAuth(ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_ANA, ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @AfterEach
    void depois() { transporte.servidor().verify(); limpar(); }

    @Test
    void cliqueDuplicadoSaiUmaVezGuardaIdsENaoExibeFalsaEntrega() {
        transporte.servidor().expect(requestTo(URL)).andExpect(header("Client-Token", "cliente-teste"))
                .andExpect(jsonPath("$.phone").value("5561999999999"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Olá 🧪\nsegunda linha")))
                .andRespond(withSuccess("{\"zaapId\":\"processo-z\",\"messageId\":\"mensagem-w\",\"id\":\"mensagem-w\"}", MediaType.APPLICATION_JSON));
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        UUID primeira = enviar();
        assertThat(enviar()).isEqualTo(primeira);
        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT payload->'aceiteNaFila'->>'idDaMensagem' FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                String.class, primeira.toString())).isEqualTo("mensagem-w"));
        assertThat(jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id = ?", String.class, primeira)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT payload->'aceiteNaFila'->>'idDoProcessamento' FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                String.class, primeira.toString())).isEqualTo("processo-z");
        publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimento)).isEqualTo(1);
    }

    @Test
    void timeoutNoTransporteRealDoAdapterSuspendeOutboxSemReenviar() {
        transporte.servidor().expect(requestTo(URL)).andRespond(withException(new IOException("timeout sintetico")));
        UUID mensagem = enviar();
        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT despacho_ambiguo_em IS NOT NULL FROM outbox_evento WHERE payload->>'mensagemId' = ?",
                Boolean.class, mensagem.toString())).isTrue());
        publicador.publicarPendentes();
        assertThat(jdbc.queryForObject("SELECT erro_entrega->>'titulo' FROM mensagem WHERE id = ?", String.class, mensagem))
                .isEqualTo("Não foi possível confirmar o envio");
    }

    @Test
    void usuarioSemAcessoNaoEnfileiraNemChamaFornecedor() {
        headers.setBearerAuth(ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_BRUNO, ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        var resposta = http.postForEntity("/api/v1/atendimentos/mensagens", pedido(), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimento)).isZero();
        publicador.publicarPendentes();
    }

    private UUID enviar() {
        var resposta = http.postForEntity("/api/v1/atendimentos/mensagens", pedido(), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return UUID.fromString(resposta.getBody().get("mensagemId").toString());
    }

    private HttpEntity<Map<String, Object>> pedido() {
        return new HttpEntity<>(Map.of("leadId", lead, "atendimentoId", atendimento, "conteudo", "Olá 🧪\nsegunda linha"), headers);
    }

    private void limpar() {
        // Banco descartavel dos ITs, sem endpoints/segredos/registro em ambiente externo.
        jdbc.update("DELETE FROM outbox_evento");
        for (String tabela : List.of("evento_timeline", "audit_log")) {
            jdbc.update("DELETE FROM " + tabela + " WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        }
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM canal_credencial WHERE id = ?", CREDENCIAL);
        jdbc.update("DELETE FROM canal WHERE nome LIKE ?", PREFIXO + "%");
    }
}
