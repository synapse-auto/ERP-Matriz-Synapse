package com.synapse.crm.app.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDeSolicitacaoResumoIa;

/** HTTP e PostgreSQL reais; servidor local representa o contrato n8n, não homologação do fornecedor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {"synapse.seguranca.token-interno=resumo-fluxo-it",
        "synapse.automacao.resumo-ia.token=webhook-teste", "synapse.automacao.resumo-ia.timeout=1s",
        "synapse.automacao.resumo-ia.maximo-de-tentativas=2"})
class ResumoIaFluxoIT extends PostgresIT {
    private static final String PREFIXO = "RESUMO-FLUXO-";
    private static final Instant INICIO = Instant.parse("2026-10-01T12:00:00Z");
    private static final AtomicInteger HTTP_STATUS = new AtomicInteger(202);
    private static final AtomicInteger CHAMADAS = new AtomicInteger();
    private static final AtomicReference<Consumer<HttpExchange>> CALLBACK = new AtomicReference<>();
    private static final AtomicReference<Throwable> ERRO_CALLBACK = new AtomicReference<>();
    private static final HttpServer WEBHOOK = iniciarWebhook();
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private PublicadorDeSolicitacaoResumoIa publicador;
    private String gestor;
    private UUID ana;

    @DynamicPropertySource
    static void configurarWebhook(DynamicPropertyRegistry props) {
        props.add("synapse.automacao.resumo-ia.url", () -> "http://127.0.0.1:" + WEBHOOK.getAddress().getPort() + "/resumo");
    }

    @BeforeEach
    void preparar() {
        HTTP_STATUS.set(202);
        CHAMADAS.set(0);
        CALLBACK.set(null);
        ERRO_CALLBACK.set(null);
        gestor = ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_GESTOR, ApoioAutenticacao.SENHA_GESTOR).accessToken();
        ana = usuario(ApoioAutenticacao.EMAIL_ANA);
    }

    @AfterEach
    void limpar() {
        CALLBACK.set(null);
        jdbc.update("DELETE FROM outbox_evento WHERE tipo = 'automacao.resumo_ia.solicitar' AND payload->>'leadId' IN "
                + "(SELECT id::text FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM comando_automacao_idempotencia WHERE atendimento_id IN "
                + "(SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN "
                + "(SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @AfterAll
    static void pararWebhook() { WEBHOOK.stop(0); }

    @Test
    void schedulerEntregaSemSegurarTransacaoEnquantoCallbackGravaEConclui() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        CALLBACK.set(exchange -> {
            try {
                assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo(pedido.toString());
                assertThat(exchange.getRequestHeaders().getFirst("CRM-Synapse-RES")).isEqualTo("webhook-teste");
                JsonNode corpo = json.readTree(exchange.getRequestBody().readAllBytes());
                assertThat(corpo.size()).isEqualTo(2);
                assertThat(corpo.path("atendimentoId").asText()).isEqualTo(ids.atendimento().toString());
                assertThat(corpo.path("leadId").asText()).isEqualTo(ids.lead().toString());
                assertThat(status(ids, pedido, "PROCESSANDO", null).getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(gravar(ids, pedido, "Resumo confirmado").getStatusCode()).isEqualTo(HttpStatus.OK);
            } catch (Throwable erro) { ERRO_CALLBACK.set(erro); }
        });
        publicador.publicarPendentes(); // Ponto @Scheduled, com ContextoDeServico e beans transacionais reais.
        assertThat(ERRO_CALLBACK.get()).isNull();
        assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("CONCLUIDO");
        assertThat(chamar(gestor, HttpMethod.GET, "/api/v1/leads/" + ids.lead(), null, null).getBody())
                .contains("Resumo confirmado");
        assertThat(jdbc.queryForObject("SELECT publicado_em IS NOT NULL FROM outbox_evento WHERE id = ?", Boolean.class, pedido)).isTrue();
        assertThat(status(ids, pedido, "CONCLUIDO", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(ids, pedido, "PROCESSANDO", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(ids, pedido, "FALHOU", "IA_FALHOU").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        publicador.publicarPendentes();
        assertThat(CHAMADAS.get()).isEqualTo(1);
    }

    @Test
    void concorrenciaCriaUmCicloEUmaOutboxMesmoComChavesDiferentes() throws Exception {
        var ids = conversa();
        var largada = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primeira = pool.submit(() -> { largada.await(); return pedir(ids.atendimento()); });
            var segunda = pool.submit(() -> { largada.await(); return pedir(ids.atendimento()); });
            largada.countDown();
            assertThat(primeira.get(15, TimeUnit.SECONDS)).isEqualTo(segunda.get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitacao_resumo_ia WHERE atendimento_id = ?", Integer.class, ids.atendimento())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE payload->>'atendimentoId' = ? AND tipo = 'automacao.resumo_ia.solicitar'", Integer.class, ids.atendimento().toString())).isEqualTo(1);
    }

    @Test
    void recusa403FalhaSemRetryEPreservaResumoAnterior() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        HTTP_STATUS.set(403);
        publicador.publicarPendentes();
        assertThat(estado(ids.atendimento()).path("erroCodigo").asText()).isEqualTo("WEBHOOK_AUTENTICACAO_RECUSADA");
        assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("FALHOU");
        assertThat(jdbc.queryForObject("SELECT resumo_ia FROM lead WHERE id = ?", String.class, ids.lead())).isEqualTo("Resumo anterior");
        publicador.publicarPendentes();
        assertThat(CHAMADAS.get()).isEqualTo(1);
        assertThat(gravar(ids, pedido, "Resposta tardia").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void cincoCentosTemBackoffLimitadoEEsgotaSemApagarResumo() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        HTTP_STATUS.set(503);
        publicador.publicarPendentes();
        assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("PENDENTE");
        publicador.publicarPendentes();
        assertThat(CHAMADAS.get()).isEqualTo(1);
        jdbc.update("UPDATE outbox_evento SET proxima_tentativa_em = ? WHERE id = ?", Timestamp.from(Instant.EPOCH), pedido);
        publicador.publicarPendentes();
        assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("FALHOU");
        assertThat(estado(ids.atendimento()).path("erroCodigo").asText()).isEqualTo("WEBHOOK_INDISPONIVEL");
        assertThat(CHAMADAS.get()).isEqualTo(2);
    }

    @Test
    void timeoutSuspendeEntregaSemInventarFalhaECadastroPermiteCallbackPosterior() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        var liberar = new CountDownLatch(1);
        CALLBACK.set(exchange -> {
            try { liberar.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException erro) { Thread.currentThread().interrupt(); }
        });
        try {
            publicador.publicarPendentes();
            assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("PENDENTE");
            assertThat(estado(ids.atendimento()).path("diagnosticoCodigo").asText()).isEqualTo("WEBHOOK_RESULTADO_INCERTO");
            publicador.publicarPendentes();
            assertThat(CHAMADAS.get()).isEqualTo(1);
            assertThat(pedir(ids.atendimento())).isEqualTo(pedido);
            assertThat(gravar(ids, pedido, "Conclusão tardia válida").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("CONCLUIDO");
            assertThat(estado(ids.atendimento()).path("diagnosticoCodigo").isNull()).isTrue();
        } finally { liberar.countDown(); }
    }

    @Test
    void falhaDaIaComunicadaEReplayNaoRegeneraNemApagaResumo() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        assertThat(status(ids, pedido, "PROCESSANDO", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(ids, pedido, "FALHOU", "IA_FALHOU").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(ids, pedido, "FALHOU", "IA_FALHOU").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT resumo_ia FROM lead WHERE id = ?", String.class, ids.lead())).isEqualTo("Resumo anterior");
        publicador.publicarPendentes();
        assertThat(CHAMADAS.get()).isZero();
        assertThat(pedir(ids.atendimento())).isNotEqualTo(pedido);
    }

    @Test
    void callbackDeOutroAtendimentoOuSemAutenticacaoNaoGrava() throws Exception {
        var ids = conversa();
        var outro = conversa();
        UUID pedido = pedir(ids.atendimento());
        assertThat(status(outro, pedido, "PROCESSANDO", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(gravar(outro, pedido, "Não aplicar").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(http.postForEntity("/internal/v1/ev05/leads/" + ids.lead() + "/resumo-status",
                Map.of("solicitacaoId", pedido, "atendimentoId", ids.atendimento(), "status", "PROCESSANDO"), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("PENDENTE");
    }

    @Test
    void conclusaoIsoladaOuTextoInvalidoNuncaConcluem() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        assertThat(status(ids, pedido, "CONCLUIDO", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(gravar(ids, pedido, " ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(estado(ids.atendimento()).path("status").asText()).isEqualTo("PENDENTE");
    }

    @Test
    void transferenciaInvalidaSnapshotEExigeEncerramentoExplicitoSemAlterarResumo() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        UUID bruno = usuario(ApoioAutenticacao.EMAIL_BRUNO);
        assertThat(chamar(gestor, HttpMethod.POST, "/api/v1/atendimentos/" + ids.atendimento() + "/transferir", null,
                Map.of("paraAtendenteId", bruno)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gravar(ids, pedido, "Resultado do dono anterior").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(estado(ids.atendimento()).path("diagnosticoCodigo").asText()).isEqualTo("CICLO_OBSOLETO");
        assertThat(status(ids, pedido, "PROCESSANDO", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(ids, pedido, "FALHOU", "CICLO_OBSOLETO").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pedir(ids.atendimento())).isNotEqualTo(pedido);
    }

    @Test
    void transferenciaDeIdaEVoltaNaoReviveOCicloAntigo() throws Exception {
        var ids = conversa();
        UUID pedido = pedir(ids.atendimento());
        for (UUID destino : java.util.List.of(usuario(ApoioAutenticacao.EMAIL_BRUNO), ana)) {
            assertThat(chamar(gestor, HttpMethod.POST,
                    "/api/v1/atendimentos/" + ids.atendimento() + "/transferir", null,
                    Map.of("paraAtendenteId", destino)).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(gravar(ids, pedido, "Não aplicar ciclo antigo").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(estado(ids.atendimento()).path("diagnosticoCodigo").asText()).isEqualTo("CICLO_OBSOLETO");
        assertThat(jdbc.queryForObject("SELECT resumo_ia FROM lead WHERE id = ?", String.class, ids.lead()))
                .isEqualTo("Resumo anterior");
    }

    @Test
    void finalizacaoImpedeCallbackTardioEIsolamentoBloqueiaColega() throws Exception {
        var ids = conversa();
        String bruno = ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_BRUNO, ApoioAutenticacao.SENHA_ATENDENTE).accessToken();
        assertThat(chamar(bruno, HttpMethod.GET, "/api/v1/atendimentos/" + ids.atendimento() + "/resumo-ia", null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(bruno, HttpMethod.POST, "/api/v1/atendimentos/" + ids.atendimento() + "/resumo-ia", UUID.randomUUID(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        UUID pedido = pedir(ids.atendimento());
        assertThat(chamar(gestor, HttpMethod.POST, "/api/v1/atendimentos/" + ids.atendimento() + "/finalizar", null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gravar(ids, pedido, "Resultado tardio").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(ids, pedido, "FALHOU", "CICLO_OBSOLETO").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID pedir(UUID atendimento) throws Exception {
        var resposta = chamar(gestor, HttpMethod.POST, "/api/v1/atendimentos/" + atendimento + "/resumo-ia", UUID.randomUUID(), null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return UUID.fromString(json.readTree(resposta.getBody()).path("solicitacaoId").asText());
    }

    private JsonNode estado(UUID atendimento) throws Exception {
        var resposta = chamar(gestor, HttpMethod.GET, "/api/v1/atendimentos/" + atendimento + "/resumo-ia", null, null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody());
    }

    private ResponseEntity<String> status(Ids ids, UUID pedido, String estado, String erro) {
        var corpo = new java.util.HashMap<String, Object>();
        corpo.put("solicitacaoId", pedido); corpo.put("atendimentoId", ids.atendimento()); corpo.put("status", estado);
        if (erro != null) corpo.put("erroCodigo", erro);
        return chamar(null, HttpMethod.POST, "/internal/v1/ev05/leads/" + ids.lead() + "/resumo-status", null, corpo);
    }

    private ResponseEntity<String> gravar(Ids ids, UUID pedido, String texto) {
        return chamar(null, HttpMethod.POST, "/internal/v1/ev05/leads/" + ids.lead() + "/resumo", pedido,
                Map.of("leadId", ids.lead(), "resumo", texto, "contextoGeradoEm", INICIO.toString()));
    }

    private ResponseEntity<String> chamar(String token, HttpMethod metodo, String rota, UUID chave, Object corpo) {
        var headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token); else headers.set("X-Synapse-Token", "resumo-fluxo-it");
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (chave != null) headers.set("Idempotency-Key", chave.toString());
        return http.exchange(rota, metodo, new HttpEntity<>(corpo, headers), String.class);
    }

    private Ids conversa() {
        UUID lead = UUID.randomUUID(); UUID atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico,ultima_interacao_em,resumo_ia) VALUES (?,?,?,'EM_ATENDIMENTO',?,'Resumo anterior')",
                lead, PREFIXO + lead, ana, Timestamp.from(INICIO));
        jdbc.update("INSERT INTO atendimento(id,lead_id,atendente_id,status,iniciado_em) VALUES (?,?,?,'EM_ATENDIMENTO',?)", atendimento, lead, ana, Timestamp.from(INICIO));
        jdbc.update("INSERT INTO mensagem(id,atendimento_id,remetente_tipo,tipo,conteudo,status_entrega,enviado_em) VALUES (?,?,'LEAD','TEXTO','Contexto sintético','ENVIADO',?)", UUID.randomUUID(), atendimento, Timestamp.from(INICIO));
        return new Ids(lead, atendimento);
    }

    private UUID usuario(String email) { return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email); }
    private record Ids(UUID lead, UUID atendimento) {}

    private static HttpServer iniciarWebhook() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/resumo", exchange -> {
                CHAMADAS.incrementAndGet();
                var callback = CALLBACK.get();
                if (callback != null) callback.accept(exchange);
                try { exchange.sendResponseHeaders(HTTP_STATUS.get(), -1); }
                finally { exchange.close(); }
            });
            server.start(); return server;
        } catch (java.io.IOException erro) { throw new IllegalStateException(erro); }
    }
}
