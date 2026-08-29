package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.atendimento.application.*;
import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.atendimento.infrastructure.avaliacao.*;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(WebhookAvaliacaoIT.Config.class)
@TestPropertySource(properties = {
    "synapse.canal.whatsapp.provedor=fake",
    "synapse.seguranca.token-interno=avaliacao-interno-fixture",
    "synapse.automacao.avaliacao.token=avaliacao-saida-fixture",
    "synapse.automacao.avaliacao.auth-header=crm-synapse-marc-auth",
    "synapse.automacao.avaliacao.timeout=2s",
    "synapse.automacao.avaliacao.reserva-expiracao=10s",
    "synapse.automacao.avaliacao.maximo-tentativas=3",
    "synapse.automacao.avaliacao.lote=2",
    "synapse.automacao.avaliacao.concorrencia=2",
    "synapse.automacao.avaliacao.fila=2",
    "synapse.automacao.avaliacao.backoff-inicial=1s",
    "synapse.automacao.avaliacao.backoff-maximo=4s"
})
class WebhookAvaliacaoIT extends PostgresIT {
    static final String TIPO = "automacao.avaliacao.iniciar";
    static final String PREFIXO = "E83-";
    static final Servidor SERVIDOR = new Servidor();
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry r) {
        r.add("synapse.automacao.avaliacao.url", SERVIDOR::url);
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary RelogioControlado relogioE83() { return new RelogioControlado(); }
    }
    static class RelogioControlado extends Clock {
        final AtomicReference<Instant> agora = new AtomicReference<>(Instant.now());
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora.get(); }
        void avancar(Duration duracao) { agora.updateAndGet(i -> i.plus(duracao)); }
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired RelogioControlado relogio;
    @Autowired PublicadorDeAvaliacao publicador;
    @Autowired PublicadorDaOutbox mensagens;
    @Autowired CanalFake canal;
    @Autowired RegistrarMensagemRecebidaUseCase registrar;
    @Autowired CircuitBreakerRegistry circuitos;
    @Autowired @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) PlatformTransactionManager manager;
    @Autowired @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDs;
    @MockitoSpyBean OutboxDeAvaliacao outbox;
    @MockitoSpyBean AtendimentoRepositorio atendimentos;
    @MockitoSpyBean LeadNoCaminhoDeMensagem leadsPorta;
    @MockitoSpyBean AvaliacaoWebhookProperties config;
    final List<UUID> leads = new ArrayList<>();
    final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
            new ch.qos.logback.core.read.ListAppender<>();
    String tokenAna;
    String tokenBruno;
    String tokenGestor;
    UUID ana;
    UUID bruno;

    @BeforeEach
    void preparar() {
        logs.start();
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PublicadorDeAvaliacao.class)).addAppender(logs);
        relogio.agora.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        SERVIDOR.limpar();
        canal.limpar();
        circuitos.circuitBreaker("automacao-avaliacao").reset();
        ana = usuario(EMAIL_ANA);
        bruno = usuario(EMAIL_BRUNO);
        tokenAna = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        tokenBruno = login(http, EMAIL_BRUNO, SENHA_ATENDENTE).accessToken();
        tokenGestor = login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
    }

    @AfterEach
    void limpar() {
        SERVIDOR.liberar.countDown();
        aguardarWorkers();
        reset(outbox, atendimentos, config);
        for (UUID id : leads) {
            jdbc.update("DELETE FROM outbox_evento WHERE payload->>'lead_id' = ? OR payload->>'leadId' = ?",
                    id.toString(), id.toString());
            jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", id);
            jdbc.update("DELETE FROM atendimento WHERE lead_id = ?", id);
            jdbc.update("DELETE FROM lead WHERE id = ?", id);
        }
        leads.clear();
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PublicadorDeAvaliacao.class)).detachAppender(logs);
        logs.stop();
    }

    @AfterAll static void parar() { SERVIDOR.fechar(); }

    @Test
    void postIndividualDoGestor_capturaResponsavelContratoExatoEColetaInterna() throws Exception {
        UUID id = criar(ana, "5561988881101");
        assertThat(finalizar(id, tokenGestor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(total(id)).isEqualTo(1);
        assertThat(SERVIDOR.recebidas).isEmpty();
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(dono(id)).isEqualTo(ana);
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).hasSize(1);
        Recebida recebida = SERVIDOR.recebidas.getFirst();
        JsonNode corpo = json.readTree(recebida.corpo());
        assertThat(corpo.size()).isEqualTo(6);
        assertThat(corpo.path("modo").asText()).isEqualTo("INICIAR_AVALIACAO");
        assertThat(corpo.path("status_finalizacao").asText()).isEqualTo("FINALIZADO");
        assertThat(corpo.path("atendimento_id").asText()).isEqualTo(id.toString());
        assertThat(corpo.path("lead_id").asText()).isEqualTo(leads.getFirst().toString());
        assertThat(corpo.path("atendente_id").asText()).isEqualTo(ana.toString());
        assertThat(corpo.path("wa_id").asText()).isEqualTo("5561988881101");
        assertThat(recebida.header()).isEqualTo("avaliacao-saida-fixture");
        assertThat(recebida.tipo()).isEqualTo("application/json");
        assertThat(recebida.metodo()).isEqualTo("POST");
        assertThat(recebida.assinaturaMeta()).isNull();
        assertThat(linha(id).get("publicado_em")).isNotNull();
        assertThat(linha(id).get("payload").toString()).doesNotContain("fixture", "auth", "http");

        assertThat(avaliar(id, null, 5).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(avaliar(id, "errado", 5).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/internal/v1/atendimentos/" + id + "/avaliacao", tokenGestor, Map.of("nota", 5))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(avaliar(id, "avaliacao-interno-fixture", 6).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(avaliar(id, "avaliacao-interno-fixture", 5).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(avaliar(id, "avaliacao-interno-fixture", 1).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM avaliacao WHERE atendimento_id = ?", UUID.class, id))
                .isEqualTo(ana);
        assertThat(finalizar(id, tokenGestor).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(total(id)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints = {1, 3})
    void loteReal_mesmoComUmItem_naoCriaPesquisaNemRetroatividade(int quantidade) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) ids.add(criar(ana, "55619888812" + String.format("%02d", i)));
        var resposta = post("/api/v1/atendimentos/finalizar-lote", tokenAna, null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (UUID id : ids) {
            assertThat(status(id)).isEqualTo("FINALIZADO");
            assertThat(total(id)).isZero();
            assertThat(finalizar(id, tokenAna).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(total(id)).isZero();
        }
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
        for (UUID lead : leads) {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_timeline WHERE lead_id = ? AND tipo = 'ATENDIMENTO_FINALIZADO'",
                        Integer.class, lead)).isEqualTo(1));
        }
    }

    @Test
    void semResponsavelOuTelefone_naoCriaPesquisaNemAtribuiQuemClicou() {
        UUID semDono = criar(null, "5561988881301");
        UUID semTelefone = criar(ana, null);
        UUID invalido = criar(ana, "000000000000");
        for (UUID id : List.of(semDono, semTelefone, invalido)) {
            assertThat(finalizar(id, tokenGestor).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(status(id)).isEqualTo("FINALIZADO");
            assertThat(total(id)).isZero();
        }
        assertThat(dono(semDono)).isNull();
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void configuracaoAusente_naoEnfileiraENaoRetomaPendenciasComoEntregues() {
        UUID pendente = criar(ana, "5561988881401");
        finalizar(pendente, tokenAna);
        doReturn(false).when(config).configurada();
        UUID novo = criar(ana, "5561988881402");
        assertThat(finalizar(novo, tokenAna).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(total(novo)).isZero();
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
        assertThat(linha(pendente).get("publicado_em")).isNull();
        assertThat(((Number) linha(pendente).get("tentativas")).intValue()).isZero();
    }

    @Test
    void colegaESemAutenticacao_naoMudamNadaNemEnfileiram() {
        UUID id = criar(ana, "5561988881501");
        assertThat(finalizar(id, tokenBruno).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(finalizar(id, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(id)).isEqualTo("EM_ATENDIMENTO");
        assertThat(dono(id)).isEqualTo(ana);
        assertThat(total(id)).isZero();
        // Prova banco/RLS: role real da aplicacao nao enxerga a conversa alheia.
        var consulta = new JdbcTemplate(chatDs);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            consulta.execute("SET LOCAL ROLE synapse_app");
            consulta.queryForObject("SELECT set_config('app.usuario_id', ?, true)", String.class, bruno.toString());
            consulta.queryForObject("SELECT set_config('app.papel', 'ATENDENTE', true)", String.class);
            assertThat(consulta.queryForObject("SELECT count(*) FROM atendimento WHERE id = ?", Integer.class, id)).isZero();
        });
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void erroDepoisDeGravarIntencao_fazRollbackDaFinalizacaoEDaOutbox() {
        UUID id = criar(ana, "5561988881601");
        doAnswer(inv -> { inv.callRealMethod(); throw new DataIntegrityViolationException("falha local fixture"); })
                .when(outbox).enfileirar(eq(id), any(), any(), anyString(), any());
        assertThat(finalizar(id, tokenAna).getStatusCode().is5xxServerError()).isTrue();
        assertThat(status(id)).isEqualTo("EM_ATENDIMENTO");
        assertThat(total(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status_basico::text FROM lead WHERE id = ?", String.class, leads.getFirst()))
                .isEqualTo("EM_ATENDIMENTO");
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void duasFinalizacoesComLeituraAntiga_umaVencedoraUmaIntencao() throws Exception {
        UUID id = criar(ana, "5561988881701");
        var leituras = new CountDownLatch(2);
        doAnswer(inv -> {
            Object antes = inv.callRealMethod();
            leituras.countDown();
            assertThat(leituras.await(5, TimeUnit.SECONDS)).isTrue();
            return antes;
        }).when(atendimentos).porId(id);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> finalizar(id, tokenAna).getStatusCode().value());
            var b = executor.submit(() -> finalizar(id, tokenGestor).getStatusCode().value());
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(total(id)).isEqualTo(1);
        assertThat(dono(id)).isEqualTo(ana);
    }

    @Test
    void leaseConcorrente_expiraRecuperaERecusaResultadosAntigos() throws Exception {
        UUID id = criar(ana, "5561988881801");
        finalizar(id, tokenAna);
        var inicio = new CountDownLatch(1);
        List<OutboxDeAvaliacao.Reserva> reservas;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { inicio.await(); return reservar(); });
            var b = executor.submit(() -> { inicio.await(); return reservar(); });
            inicio.countDown();
            reservas = new ArrayList<>(a.get(5, TimeUnit.SECONDS));
            reservas.addAll(b.get(5, TimeUnit.SECONDS));
        }
        assertThat(reservas).hasSize(1);
        var antiga = reservas.getFirst();
        relogio.avancar(Duration.ofSeconds(11));
        var nova = reservar().getFirst();
        assertThat(nova.eventoId()).isEqualTo(antiga.eventoId());
        assertThat(nova.token()).isNotEqualTo(antiga.token());
        assertThat(nova.tentativas()).isEqualTo(2);
        assertThat(servico(() -> outbox.concluir(antiga, relogio.instant()))).isFalse();
        assertThat(servico(() -> outbox.falhar(antiga, relogio.instant(), relogio.instant(), "ANTIGA", true))).isFalse();
        assertThat(servico(() -> outbox.concluir(nova, relogio.instant()))).isTrue();
    }

    @ParameterizedTest @ValueSource(ints = {429, 500, 503})
    void recuperavel_respeitaBackoffEsgotaSemApagarNemMarcarPublicado(int statusHttp) {
        UUID id = criar(ana, "5561988881901");
        finalizar(id, tokenAna);
        SERVIDOR.status = statusHttp;
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            publicador.publicarPendentes();
            aguardarWorkers();
            assertThat(SERVIDOR.recebidas).hasSize(tentativa);
            assertThat(((Number) linha(id).get("tentativas")).intValue()).isEqualTo(tentativa);
            publicador.publicarPendentes();
            aguardarWorkers();
            assertThat(SERVIDOR.recebidas).hasSize(tentativa);
            relogio.avancar(Duration.ofSeconds(4));
        }
        assertThat(linha(id).get("esgotado_em")).isNotNull();
        assertThat(linha(id).get("publicado_em")).isNull();
        assertThat(linha(id).get("ultimo_erro")).isEqualTo("HTTP_" + statusHttp);
        assertThat(total(id)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints = {301, 401, 403, 422})
    void permanente_umaTentativaSemSeguirRedirectOuVazarResposta(int statusHttp) {
        UUID id = criar(ana, "5561988882001");
        finalizar(id, tokenAna);
        SERVIDOR.status = statusHttp;
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).hasSize(1);
        assertThat(SERVIDOR.redirecionadas).isZero();
        assertThat(linha(id).get("esgotado_em")).isNotNull();
        assertThat(linha(id).get("ultimo_erro")).isEqualTo("HTTP_" + statusHttp);
        assertThat(linha(id).get("publicado_em")).isNull();
        assertThat(logs.list).allSatisfy(evento -> {
            assertThat(evento.getFormattedMessage()).doesNotContain("avaliacao-saida-fixture", "5561988882001", "resposta privada", "http://");
            assertThat(evento.getThrowableProxy()).isNull();
        });
    }

    @Test
    void httpBloqueado_naoReteveTransacaoNemImpedeMensagemNormal_eTimeoutRepeteMesmoId() throws Exception {
        UUID id = criar(ana, "5561988882101");
        SERVIDOR.bloquear();
        assertThat(finalizar(id, tokenAna).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(SERVIDOR.recebidas).isEmpty();
        publicador.publicarPendentes(); // ponto @Scheduled real, nao operacao interna
        assertThat(SERVIDOR.entrou.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(ContextoDeServico.ativo()).isFalse();
        // Nenhuma conexao do banco fica em transacao enquanto o fake segura o HTTP.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND state = 'idle in transaction'
                """, Integer.class)).isZero();
        UUID outro = criar(bruno, "5561988882102");
        var enviado = post("/api/v1/atendimentos/mensagens", tokenBruno,
                Map.of("leadId", leads.getLast().toString(), "conteudo", "Mensagem independente"));
        assertThat(enviado.getStatusCode()).isEqualTo(HttpStatus.OK);
        // O publisher legado usa este Clock, mas seu INSERT usa Instant.now(): alinhar
        // o relogio controlado ao instante ja persistido, sem esperar nem repetir o job.
        relogio.agora.set(Instant.now());
        mensagens.publicarPendentes();
        assertThat(canal.enviados()).anyMatch(e -> e.telefoneDestino().equals("5561988882102"));
        assertThat(status(outro)).isEqualTo("EM_ATENDIMENTO");
        aguardarWorkers(); // condicao: timeout finito do HTTP, nunca sleep
        assertThat(linha(id).get("publicado_em")).isNull();
        String payloadInicial = SERVIDOR.recebidas.getFirst().corpo();
        SERVIDOR.liberar.countDown();
        relogio.avancar(Duration.ofSeconds(2));
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).hasSize(2);
        assertThat(json.readTree(SERVIDOR.recebidas.getLast().corpo())).isEqualTo(json.readTree(payloadInicial));
        assertThat(linha(id).get("publicado_em")).isNotNull();
    }

    @Test
    void morteEmTodaReserva_temLimiteEPendenciaInspecionavel() {
        UUID id = criar(ana, "5561988882201");
        finalizar(id, tokenAna);
        for (int i = 0; i < 3; i++) {
            assertThat(reservar()).hasSize(1);
            relogio.avancar(Duration.ofSeconds(11));
        }
        assertThat(reservar()).isEmpty();
        assertThat(linha(id).get("esgotado_em")).isNotNull();
        assertThat(linha(id).get("publicado_em")).isNull();
        assertThat(servico(outbox::esgotadas)).isPositive();
    }

    @Test
    void snapshotAntigoNaoReabreAtendimentoFinalizado() {
        UUID id = criar(ana, "5561988882301");
        var antigo = servico(() -> atendimentos.porId(id).orElseThrow());
        finalizar(id, tokenAna);
        assertThatThrownBy(() -> servico(() -> atendimentos.salvar(antigo.transferirPara(bruno))))
                .isInstanceOf(AtendimentoJaFinalizadoException.class);
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(dono(id)).isEqualTo(ana);
    }

    @Test
    void loteComItemRecusado_preservaOsOutrosSemPesquisa() {
        UUID jaFechado = criar(ana, "5561988882401");
        UUID aberto = criar(ana, "5561988882402");
        var fechadoAntigo = servico(() -> atendimentos.porId(jaFechado).orElseThrow());
        jdbc.update("UPDATE atendimento SET status = 'FINALIZADO', finalizado_em = now() WHERE id = ?", jaFechado);
        var abertoAntes = servico(() -> atendimentos.porId(aberto).orElseThrow());
        doReturn(List.of(fechadoAntigo, abertoAntes)).when(atendimentos).abertosVisiveis();
        var resposta = post("/api/v1/atendimentos/finalizar-lote", tokenAna, null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"recusados\":1", "\"finalizados\":1");
        assertThat(status(aberto)).isEqualTo("FINALIZADO");
        assertThat(total(aberto)).isZero();
        assertThat(total(jaFechado)).isZero();
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void loteComFalhaNoSegundoItem_fazRollbackSemNotificacao() {
        UUID primeiro = criar(ana, "5561988882501");
        UUID segundo = criar(ana, "5561988882502");
        var antesA = servico(() -> atendimentos.porId(primeiro).orElseThrow());
        var antesB = servico(() -> atendimentos.porId(segundo).orElseThrow());
        doReturn(List.of(antesA, antesB)).when(atendimentos).abertosVisiveis();
        doThrow(new DataIntegrityViolationException("falha local fixture")).when(atendimentos).porIdParaAlteracao(segundo);
        assertThat(post("/api/v1/atendimentos/finalizar-lote", tokenAna, null).getStatusCode().is5xxServerError()).isTrue();
        for (UUID id : List.of(primeiro, segundo)) {
            assertThat(status(id)).isEqualTo("EM_ATENDIMENTO");
            assertThat(total(id)).isZero();
        }
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
        for (UUID id : leads) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_timeline WHERE lead_id = ? AND tipo = 'ATENDIMENTO_FINALIZADO'",
                    Integer.class, id)).isZero();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void transferenciaConcorrente_preservaResponsavelDaFinalizacaoVencedora(boolean transferenciaPrimeiro) throws Exception {
        UUID id = criar(ana, "5561988882601");
        var travou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var chamadas = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv -> {
            Object estado = inv.callRealMethod();
            if (chamadas.incrementAndGet() == 1) {
                travou.countDown();
                assertThat(liberar.await(8, TimeUnit.SECONDS)).isTrue();
            }
            return estado;
        }).when(atendimentos).porIdParaAlteracao(id);
        Supplier<ResponseEntity<String>> transferir = () -> post("/api/v1/atendimentos/" + id + "/transferir",
                tokenGestor, Map.of("paraAtendenteId", bruno.toString()));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var primeira = executor.submit(() -> transferenciaPrimeiro ? transferir.get() : finalizar(id, tokenGestor));
            assertThat(travou.await(5, TimeUnit.SECONDS)).isTrue();
            var segunda = executor.submit(() -> transferenciaPrimeiro ? finalizar(id, tokenGestor) : transferir.get());
            try { esperarDisputaNoBanco(); } finally { liberar.countDown(); }
            assertThat(primeira.get(8, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(segunda.get(8, TimeUnit.SECONDS).getStatusCode())
                    .isEqualTo(transferenciaPrimeiro ? HttpStatus.OK : HttpStatus.CONFLICT);
        } finally { liberar.countDown(); }
        UUID esperado = transferenciaPrimeiro ? bruno : ana;
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(dono(id)).isEqualTo(esperado);
        assertThat(json.readTree(linha(id).get("payload").toString()).path("atendente_id").asText())
                .isEqualTo(esperado.toString());
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leads.getFirst()))
                .isEqualTo(esperado);
    }

    @Test
    void envioConcorrenteAposFinalizacao_abreOutroAtendimentoSemMudarSnapshot() throws Exception {
        UUID id = criar(ana, "5561988882701");
        var travou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        doAnswer(inv -> {
            Object estado = inv.callRealMethod();
            travou.countDown();
            assertThat(liberar.await(8, TimeUnit.SECONDS)).isTrue();
            return estado;
        }).when(atendimentos).porIdParaAlteracao(id);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var finalizar = executor.submit(() -> finalizar(id, tokenGestor));
            assertThat(travou.await(5, TimeUnit.SECONDS)).isTrue();
            var enviar = executor.submit(() -> post("/api/v1/atendimentos/mensagens", tokenGestor,
                    Map.of("leadId", leads.getFirst().toString(), "conteudo", "nova conversa")));
            try { esperarDisputaNoBanco(); } finally { liberar.countDown(); }
            assertThat(finalizar.get(8, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(enviar.get(8, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally { liberar.countDown(); }
        assertThat(dono(id)).isEqualTo(ana);
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(json.readTree(linha(id).get("payload").toString()).path("atendente_id").asText()).isEqualTo(ana.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atendimento WHERE lead_id = ? AND status <> 'FINALIZADO'",
                Integer.class, leads.getFirst())).isEqualTo(1);
        assertThat(total(id)).isEqualTo(1);
    }

    void esperarDisputaNoBanco() {
        await().atMost(Duration.ofSeconds(5)).until(() ->
            jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'",
                    Integer.class) > 0);
    }

    @Test
    void chaveDuravelNaoReescreveSnapshot_eNovoAtendimentoDoMesmoLeadPodeGerarPesquisa() {
        UUID primeiro = criar(ana, "5561988882801");
        finalizar(primeiro, tokenGestor);
        UUID lead = leads.getFirst();
        Object original = linha(primeiro).get("payload");
        servico(() -> {
            outbox.enfileirar(primeiro, lead, bruno, "5561988882802", relogio.instant());
            return null;
        });
        assertThat(total(primeiro)).isEqualTo(1);
        assertThat(linha(primeiro).get("payload").toString()).isEqualTo(original.toString());
        UUID segundo = UUID.randomUUID();
        jdbc.update("INSERT INTO atendimento (id, lead_id, atendente_id, status) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                segundo, lead, bruno);
        assertThat(finalizar(segundo, tokenGestor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(total(segundo)).isEqualTo(1);
        assertThat(linha(segundo).get("id")).isNotEqualTo(linha(primeiro).get("id"));
    }

    @Test
    void circuitoDaAvaliacaoAberto_ePayloadCorrompido_naoEnviamHttp() {
        UUID id = criar(ana, "5561988882901");
        finalizar(id, tokenAna);
        circuitos.circuitBreaker("automacao-avaliacao").transitionToOpenState();
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
        assertThat(linha(id).get("ultimo_erro")).isEqualTo("CIRCUITO_ABERTO");
        assertThat(linha(id).get("esgotado_em")).isNull();
        circuitos.circuitBreaker("automacao-avaliacao").reset();
        jdbc.update("UPDATE outbox_evento SET payload = jsonb_set(payload, '{modo}', '\"INVALIDO\"') WHERE id = ?",
                linha(id).get("id"));
        relogio.avancar(Duration.ofSeconds(2));
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
        assertThat(linha(id).get("ultimo_erro")).isEqualTo("PAYLOAD_INVALIDO");
        assertThat(linha(id).get("esgotado_em")).isNotNull();
        assertThat(linha(id).get("publicado_em")).isNull();
    }

    @Test
    void transferenciaDevolucaoESaida_naoGeramPesquisa() {
        UUID id = criar(ana, "5561988883001");
        assertThat(post("/api/v1/atendimentos/" + id + "/entrar", tokenGestor, null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/v1/atendimentos/" + id + "/sair", tokenGestor, null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post("/api/v1/atendimentos/" + id + "/transferir", tokenGestor,
                Map.of("paraAtendenteId", bruno.toString())).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/atendimentos/" + id + "/transferir", tokenGestor, null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(total(id)).isZero();
        assertThat(status(id)).isEqualTo("EM_IA");
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void recebimentoPausadoAntesDoContador_eFinalizacaoNaoEntramEmDeadlock() throws Exception {
        UUID id = criar(ana, "5561988883002");
        UUID lead = leads.getFirst();
        var recebimentoEntrou = new CountDownLatch(1);
        var liberarRecebimento = new CountDownLatch(1);
        var finalizacaoTravouNoLead = new CountDownLatch(1);
        var liberarFinalizacao = new CountDownLatch(1);
        doAnswer(inv -> {
            recebimentoEntrou.countDown();
            assertThat(liberarRecebimento.await(5, TimeUnit.SECONDS)).isTrue();
            return inv.callRealMethod();
        }).when(leadsPorta).registrarInteracao(eq(lead), any(), eq(0), eq(1));
        doAnswer(inv -> {
            Object resultado = inv.callRealMethod();
            finalizacaoTravouNoLead.countDown();
            assertThat(liberarFinalizacao.await(5, TimeUnit.SECONDS)).isTrue();
            return resultado;
        }).when(leadsPorta).bloquearParaAtendimento(lead);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var recebimento = executor.submit(() -> servico(() ->
                    registrarRecebida(lead)));
            assertThat(recebimentoEntrou.await(5, TimeUnit.SECONDS)).isTrue();
            var finalizacao = executor.submit(() -> finalizar(id, tokenGestor));
            assertThat(finalizacaoTravouNoLead.await(5, TimeUnit.SECONDS)).isTrue();
            // O recebimento ja tem a FK KEY SHARE no atendimento; liberar os dois pontos
            // força exatamente a disputa real, sem sleeps nem repeticao cega.
            liberarRecebimento.countDown();
            liberarFinalizacao.countDown();
            assertThat(finalizacao.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(recebimento.get(10, TimeUnit.SECONDS)).isNotNull();
        } finally {
            liberarRecebimento.countDown();
            liberarFinalizacao.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(total(id)).isEqualTo(1);
    }

    Object registrarRecebida(UUID lead) {
        return ContextoDeServico.buscarComo("teste-recebimento-concorrente", () ->
                new TransactionTemplate(manager).execute(tx -> registrar.executar(
                        new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                                lead, null, null, "mensagem recebida durante finalizacao"))));
    }

    List<OutboxDeAvaliacao.Reserva> reservar() {
        return servico(() -> outbox.reservar(1, 3, relogio.instant(), relogio.instant().plusSeconds(10)));
    }
    <T> T servico(Supplier<T> acao) {
        return ContextoDeServico.buscarComo("teste-avaliacao",
                () -> new TransactionTemplate(manager).execute(tx -> acao.get()));
    }
    void aguardarWorkers() {
        await().atMost(Duration.ofSeconds(8)).until(() -> publicador.emAndamento() == 0);
    }
    UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }
    UUID criar(UUID dono, String telefone) {
        UUID lead = UUID.randomUUID();
        UUID atendimento = UUID.randomUUID();
        leads.add(lead);
        jdbc.update("""
                INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico, ultima_interacao_em)
                VALUES (?, ?, ?, ?, ?::status_basico_lead, ?)
                """, lead, PREFIXO + lead, telefone, dono, dono == null ? "IA" : "EM_ATENDIMENTO",
                Timestamp.from(relogio.instant()));
        jdbc.update("""
                INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)
                VALUES (?, ?, ?, ?::status_atendimento, ?)
                """, atendimento, lead, dono, dono == null ? "EM_IA" : "EM_ATENDIMENTO",
                Timestamp.from(relogio.instant().minusSeconds(60)));
        return atendimento;
    }
    String status(UUID id) {
        return jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, id);
    }
    UUID dono(UUID id) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, id);
    }
    int total(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo = ? AND payload->>'atendimento_id' = ?",
                Integer.class, TIPO, id.toString());
    }
    Map<String, Object> linha(UUID id) {
        return jdbc.queryForMap("""
                SELECT id, payload, tentativas, publicado_em, esgotado_em, ultimo_erro, avaliacao_reserva_id
                FROM outbox_evento WHERE tipo = ? AND payload->>'atendimento_id' = ?
                """, TIPO, id.toString());
    }
    ResponseEntity<String> finalizar(UUID id, String token) {
        return post("/api/v1/atendimentos/" + id + "/finalizar", token, null);
    }
    ResponseEntity<String> post(String url, String token, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(corpo, headers), String.class);
    }
    ResponseEntity<String> avaliar(UUID id, String token, int nota) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.set("X-Synapse-Token", token);
        return http.exchange("/internal/v1/atendimentos/" + id + "/avaliacao", HttpMethod.POST,
                new HttpEntity<>(Map.of("nota", nota), headers), String.class);
    }

    record Recebida(String corpo, String header, String tipo, String metodo, String assinaturaMeta) {}
    static class Servidor {
        final HttpServer servidor;
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final List<Recebida> recebidas = new CopyOnWriteArrayList<>();
        volatile int status = 204;
        volatile int redirecionadas;
        volatile CountDownLatch liberar = new CountDownLatch(0);
        volatile CountDownLatch entrou = new CountDownLatch(0);
        Servidor() {
            try {
                servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                servidor.setExecutor(executor);
                servidor.createContext("/avaliacao", troca -> {
                    recebidas.add(new Recebida(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                            troca.getRequestHeaders().getFirst("crm-synapse-marc-auth"),
                            troca.getRequestHeaders().getFirst("Content-Type"), troca.getRequestMethod(),
                            troca.getRequestHeaders().getFirst("X-Hub-Signature-256")));
                    entrou.countDown();
                    try {
                        if (!liberar.await(10, TimeUnit.SECONDS)) throw new AssertionError("fixture nao liberada");
                        troca.getResponseHeaders().add("Location", url().replace("/avaliacao", "/outro"));
                        if (status >= 200 && status < 300) {
                            troca.sendResponseHeaders(status, -1);
                        } else {
                            byte[] privado = "resposta privada avaliacao-saida-fixture 5561988882001".getBytes(StandardCharsets.UTF_8);
                            troca.sendResponseHeaders(status, privado.length);
                            troca.getResponseBody().write(privado);
                        }
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { troca.close(); }
                });
                servidor.createContext("/outro", troca -> { redirecionadas++; troca.sendResponseHeaders(204, -1); troca.close(); });
                servidor.start();
            } catch (IOException e) { throw new IllegalStateException(e); }
        }
        String url() { return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/avaliacao"; }
        void bloquear() { liberar = new CountDownLatch(1); entrou = new CountDownLatch(1); }
        void limpar() { liberar.countDown(); liberar = new CountDownLatch(0); entrou = new CountDownLatch(0); recebidas.clear(); status = 204; redirecionadas = 0; }
        void fechar() { liberar.countDown(); servidor.stop(0); executor.close(); }
    }
}
