package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

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
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;
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
    @Autowired ProcessadorDeWebhookEntrada processador;
    @Autowired SolicitacaoDeAvaliacao solicitacao;
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
        reset(outbox, atendimentos, config, leadsPorta);
        jdbc.update("DELETE FROM webhook_entrada WHERE id_externo LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_recebida_idempotencia WHERE wamid LIKE ?", PREFIXO + "%");
        for (UUID id : leads) {
            jdbc.update("DELETE FROM outbox_evento WHERE payload->>'lead_id' = ? OR payload->>'leadId' = ?",
                    id.toString(), id.toString());
            jdbc.update("DELETE FROM comando_automacao_idempotencia WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", id);
            jdbc.update("DELETE FROM mensagem_automacao_idempotencia WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", id);
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
    void postIndividualDoGestor_naoSolicitaAvaliacaoMasColetaRespostaInterna() throws Exception {
        UUID id = criar(ana, "5561988881101");
        assertThat(finalizar(id, tokenGestor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(total(id)).isZero();
        assertThat(SERVIDOR.recebidas).isEmpty();
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(dono(id)).isEqualTo(ana);
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();

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
        assertThat(total(id)).isZero();
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
        prepararAvaliacao(pendente);
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
    void finalizacaoNaoDependeMaisDaIntencaoDeAvaliacao() {
        UUID id = criar(ana, "5561988881601");
        doAnswer(inv -> { inv.callRealMethod(); throw new DataIntegrityViolationException("falha local fixture"); })
                .when(outbox).enfileirar(eq(id), any(), any(), anyString(), any());
        assertThat(finalizar(id, tokenAna).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(total(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status_basico::text FROM lead WHERE id = ?", String.class, leads.getFirst()))
                .isEqualTo("FINALIZADO");
        verify(outbox, never()).enfileirar(eq(id), any(), any(), anyString(), any());
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
        assertThat(total(id)).isZero();
        assertThat(dono(id)).isEqualTo(ana);
    }

    @Test
    void leaseConcorrente_expiraRecuperaERecusaResultadosAntigos() throws Exception {
        UUID id = criar(ana, "5561988881801");
        finalizar(id, tokenAna);
        prepararAvaliacao(id);
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
        prepararAvaliacao(id);
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
        prepararAvaliacao(id);
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
        prepararAvaliacao(id);
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
        prepararAvaliacao(id);
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
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leads.getFirst()))
                .isEqualTo(esperado);
        assertThat(total(id)).isZero();
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atendimento WHERE lead_id = ? AND status <> 'FINALIZADO'",
                Integer.class, leads.getFirst())).isEqualTo(1);
        assertThat(total(id)).isZero();
    }

    void esperarDisputaNoBanco() {
        await().atMost(Duration.ofSeconds(5)).until(() ->
            jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'",
                    Integer.class) > 0);
    }

    @Test
    void chaveDuravelNaoReescreveSnapshot_eSolicitacoesExplicitasUsamAtendimentosDistintos() {
        UUID primeiro = criar(ana, "5561988882801");
        finalizar(primeiro, tokenGestor);
        prepararAvaliacao(primeiro);
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
        prepararAvaliacao(segundo);
        assertThat(total(segundo)).isEqualTo(1);
        assertThat(linha(segundo).get("id")).isNotEqualTo(linha(primeiro).get("id"));
    }

    @Test
    void circuitoDaAvaliacaoAberto_ePayloadCorrompido_naoEnviamHttp() {
        UUID id = criar(ana, "5561988882901");
        finalizar(id, tokenAna);
        prepararAvaliacao(id);
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
        String telefone = "5561988884101";
        UUID id = criar(ana, telefone);
        UUID lead = leads.getLast();
        int mensagensAntes = contador(lead, "num_mensagens");
        String wamid = PREFIXO + "rx-" + id;
        postarWebhookRecebido(wamid, telefone, "mensagem recebida durante finalizacao");
        executarDisputaDeterministica(
                lead,
                () -> {
                    processador.processarPendentes();
                    return null;
                },
                () -> finalizar(id, tokenGestor),
                (ignorado, finalizacao) ->
                        assertThat(finalizacao.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(quantidadeDeMensagens(id)).isEqualTo(1);
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(dono(id)).isEqualTo(ana);
        assertThat(total(id)).isZero();
        assertThat(contador(lead, "num_mensagens")).isEqualTo(mensagensAntes + 1);
        assertThat(jdbc.queryForObject(
                        "SELECT processado_em IS NOT NULL FROM webhook_entrada WHERE id_externo = ?",
                        Boolean.class,
                        wamid))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT tentativas FROM webhook_entrada WHERE id_externo = ?", Integer.class, wamid))
                .isZero();
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void recebimentoAposFinalizacaoConfirmada_abreOutroAtendimentoSemMudarSnapshot() {
        String telefone = "5561988884102";
        UUID id = criar(ana, telefone);
        UUID lead = leads.getLast();
        assertThat(finalizar(id, tokenGestor).getStatusCode()).isEqualTo(HttpStatus.OK);
        String wamid = PREFIXO + "rx-depois-" + id;
        postarWebhookRecebido(wamid, telefone, "nova conversa apos encerramento");
        processador.processarPendentes();
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(dono(id)).isEqualTo(ana);
        assertThat(total(id)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM atendimento WHERE lead_id = ? AND status <> 'FINALIZADO'",
                        Integer.class,
                        lead))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void respostaDaAutomacaoConcorrenteComFinalizacao_naoDeadlockNemDuplica() throws Exception {
        UUID id = criar(null, "5561988884103");
        UUID lead = leads.getLast();
        executarDisputaDeterministica(
                lead,
                () -> postInterno("/internal/v1/atendimentos/" + id + "/responder", PREFIXO + "ia-" + id,
                        Map.of("conteudo", "resposta da IA durante encerramento")),
                () -> finalizar(id, tokenGestor),
                (resposta, finalizacao) -> {
                    assertThat(finalizacao.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
                });
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(total(id)).isZero();
        assertThat(quantidadeDeMensagens(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_evento WHERE tipo = 'canal.mensagem.enviar' AND payload->>'atendimentoId' = ?",
                        Integer.class,
                        id.toString()))
                .isEqualTo(1);
        assertThat(postInterno("/internal/v1/atendimentos/" + id + "/responder", PREFIXO + "ia-depois-" + id,
                Map.of("conteudo", "depois do fechamento")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(quantidadeDeMensagens(id)).isEqualTo(1);
    }

    @Test
    void registroDeMensagemJaEnviadaConcorrenteComFinalizacao_preservaIdempotencia() throws Exception {
        UUID id = criar(ana, "5561988884104");
        UUID lead = leads.getLast();
        String wamid = PREFIXO + "wamid-" + id;
        executarDisputaDeterministica(
                lead,
                () -> postInterno("/internal/v1/atendimentos/" + id + "/mensagens-enviadas", null,
                        Map.of("wamid", wamid, "tipo", "TEXTO", "conteudo", "ja enviada pela IA")),
                () -> finalizar(id, tokenGestor),
                (registro, finalizacao) -> {
                    assertThat(finalizacao.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(registro.getBody()).contains("\"idempotente\":false");
                });
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(total(id)).isZero();
        assertThat(quantidadeDeMensagens(id)).isEqualTo(1);
        var repeticao = postInterno("/internal/v1/atendimentos/" + id + "/mensagens-enviadas", null,
                Map.of("wamid", wamid, "tipo", "TEXTO", "conteudo", "ja enviada pela IA"));
        assertThat(repeticao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeticao.getBody()).contains("\"idempotente\":true");
        assertThat(quantidadeDeMensagens(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem_automacao_idempotencia WHERE wamid = ?",
                        Integer.class,
                        wamid))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_evento WHERE tipo = 'canal.mensagem.enviar' AND payload->>'atendimentoId' = ?",
                        Integer.class,
                        id.toString()))
                .isZero();
    }

    @Test
    void transferenciaConcorrenteComRecebimento_semCicloNemAvaliacao() throws Exception {
        String telefone = "5561988884105";
        UUID id = criar(ana, telefone);
        UUID lead = leads.getLast();
        int mensagensAntes = contador(lead, "num_mensagens");
        String wamid = PREFIXO + "rx-tr-" + id;
        postarWebhookRecebido(wamid, telefone, "mensagem durante transferencia");
        executarDisputaDeterministica(
                lead,
                () -> {
                    processador.processarPendentes();
                    return null;
                },
                () -> post("/api/v1/atendimentos/" + id + "/transferir", tokenGestor,
                        Map.of("paraAtendenteId", bruno.toString())),
                (ignorado, transferencia) ->
                        assertThat(transferencia.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(status(id)).isEqualTo("EM_ATENDIMENTO");
        assertThat(dono(id)).isEqualTo(bruno);
        assertThat(total(id)).isZero();
        assertThat(quantidadeDeMensagens(id)).isEqualTo(1);
        assertThat(contador(lead, "num_mensagens")).isEqualTo(mensagensAntes + 1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM evento_timeline WHERE atendimento_id = ? AND tipo LIKE '%TRANSFERIDO%'",
                        Integer.class, id)).isEqualTo(1));
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void loteConcorrenteComRecebimento_semDeadlockNemPesquisa() throws Exception {
        String telefone = "5561988884106";
        UUID aberto = criar(ana, telefone);
        UUID leadAberto = leads.getLast();
        UUID jaFechado = criar(ana, "5561988884107");
        jdbc.update("UPDATE atendimento SET status = 'FINALIZADO', finalizado_em = now() WHERE id = ?", jaFechado);
        var abertoAntes = servico(() -> atendimentos.porId(aberto).orElseThrow());
        var fechadoAntes = servico(() -> atendimentos.porId(jaFechado).orElseThrow());
        doReturn(List.of(abertoAntes, fechadoAntes)).when(atendimentos).abertosVisiveis();
        String wamid = PREFIXO + "rx-lote-" + aberto;
        postarWebhookRecebido(wamid, telefone, "mensagem durante lote");
        executarDisputaDeterministica(
                leadAberto,
                () -> {
                    processador.processarPendentes();
                    return null;
                },
                () -> post("/api/v1/atendimentos/finalizar-lote", tokenAna, null),
                (ignorado, lote) -> {
                    assertThat(lote.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(lote.getBody()).contains("\"recusados\":1", "\"finalizados\":1");
                });
        assertThat(status(aberto)).isEqualTo("FINALIZADO");
        assertThat(total(aberto)).isZero();
        assertThat(total(jaFechado)).isZero();
        assertThat(quantidadeDeMensagens(aberto)).isEqualTo(1);
        publicador.publicarPendentes();
        aguardarWorkers();
        assertThat(SERVIDOR.recebidas).isEmpty();
    }

    @Test
    void isolamentoTransacional_recebimentoPausaAntesDoLeadEFinalizacaoHttp() throws Exception {
        UUID id = criar(ana, "5561988884108");
        UUID lead = leads.getLast();
        executarDisputaDeterministica(
                lead,
                () -> ContextoDeServico.buscarComo("teste-recebimento-concorrente", () ->
                        registrar.executar(new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                                lead, null, null, "isolamento transacional"))),
                () -> finalizar(id, tokenGestor),
                (resultado, finalizacao) -> {
                    assertThat(finalizacao.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(resultado).isNotNull();
                });
        assertThat(quantidadeDeMensagens(id)).isEqualTo(1);
        assertThat(status(id)).isEqualTo("FINALIZADO");
        assertThat(total(id)).isZero();
    }

    <M, A> void executarDisputaDeterministica(
            UUID lead,
            Callable<M> caminhoDaMensagem,
            Callable<A> caminhoDaAlteracao,
            java.util.function.BiConsumer<M, A> assercoes) throws Exception {
        var mensagemPausou = new CountDownLatch(1);
        var liberarMensagem = new CountDownLatch(1);
        var alteracaoTravouLead = new CountDownLatch(1);
        var liberarAlteracao = new CountDownLatch(1);
        doAnswer(inv -> {
            mensagemPausou.countDown();
            assertThat(liberarMensagem.await(8, TimeUnit.SECONDS)).isTrue();
            return inv.callRealMethod();
        }).when(leadsPorta).registrarInteracao(eq(lead), any(), anyInt(), anyInt());
        doAnswer(inv -> {
            Object resultado = inv.callRealMethod();
            alteracaoTravouLead.countDown();
            assertThat(liberarAlteracao.await(8, TimeUnit.SECONDS)).isTrue();
            return resultado;
        }).when(leadsPorta).bloquearParaAtendimento(lead);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futuraMensagem = executor.submit(caminhoDaMensagem);
            assertThat(mensagemPausou.await(8, TimeUnit.SECONDS)).isTrue();
            var futuraAlteracao = executor.submit(caminhoDaAlteracao);
            assertThat(alteracaoTravouLead.await(8, TimeUnit.SECONDS)).isTrue();
            // A mensagem ja inseriu (KEY SHARE no atendimento desta fixture) e a
            // alteracao ja obteve FOR UPDATE neste lead. Liberar os dois forca o ciclo.
            liberarMensagem.countDown();
            liberarAlteracao.countDown();
            M resultadoMensagem = futuraMensagem.get(10, TimeUnit.SECONDS);
            A resultadoAlteracao = futuraAlteracao.get(10, TimeUnit.SECONDS);
            assercoes.accept(resultadoMensagem, resultadoAlteracao);
        } finally {
            liberarMensagem.countDown();
            liberarAlteracao.countDown();
            reset(leadsPorta);
        }
    }

    void postarWebhookRecebido(String idExterno, String telefone, String texto) {
        String payload = "{\"id\":\"" + idExterno + "\",\"de\":\"" + telefone
                + "\",\"nome\":\"Cliente\",\"texto\":\"" + texto + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", CanalFake.ASSINATURA_VALIDA);
        assertThat(http.postForEntity("/webhook/canal", new HttpEntity<>(payload, headers), String.class)
                .getStatusCode()
                .is2xxSuccessful())
                .isTrue();
    }

    ResponseEntity<String> postInterno(String url, String chave, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", "avaliacao-interno-fixture");
        if (chave != null) {
            headers.set("Idempotency-Key", chave);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(corpo, headers), String.class);
    }

    int quantidadeDeMensagens(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimentoId);
    }

    int contador(UUID leadId, String coluna) {
        return jdbc.queryForObject("SELECT " + coluna + " FROM lead WHERE id = ?", Integer.class, leadId);
    }

    List<OutboxDeAvaliacao.Reserva> reservar() {
        return servico(() -> outbox.reservar(1, 3, relogio.instant(), relogio.instant().plusSeconds(10)));
    }
    void prepararAvaliacao(UUID atendimentoId) {
        var finalizado = servico(() -> atendimentos.porId(atendimentoId).orElseThrow());
        servico(() -> {
            solicitacao.preparar(finalizado);
            return null;
        });
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
                INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico,
                                  ultima_interacao_em, ultima_mensagem_do_lead_em)
                VALUES (?, ?, ?, ?, ?::status_basico_lead, ?, ?)
                """, lead, PREFIXO + lead, telefone, dono, dono == null ? "IA" : "EM_ATENDIMENTO",
                Timestamp.from(relogio.instant()), Timestamp.from(relogio.instant()));
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
