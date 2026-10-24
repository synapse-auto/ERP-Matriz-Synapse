package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.application.referencia.CorrelacionarMensagemDoCanalUseCase;
import com.synapse.crm.atendimento.application.referencia.CorrelacionarMensagemDoCanalUseCase.Vinculo;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusComIdentidade;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Nenhuma rota permissiva: injecao local vai pela fila; o webhook HTTP real deve recusar. */
@SpringBootTest(classes = SynapseCrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(ZApiEntradaLocalIT.ConfiguracaoLocal.class)
@RecordApplicationEvents
@TestPropertySource(properties = {"synapse.canal.whatsapp.provedor=z-api", "synapse.canal.whatsapp.numero-principal=ZAPI-LOCAL-A", "synapse.canal.z-api.habilitado=false"})
class ZApiEntradaLocalIT extends PostgresIT {
    private static final String INSTANCIA = "ZAPI-LOCAL-A";
    private static final String PREFIXO = "ZAPI-ENTRADA-";
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1"))
            .withEnv("MINIO_ROOT_USER", "zapi-local-access").withEnv("MINIO_ROOT_PASSWORD", "zapi-local-secret-storage")
            .withCommand("server", "/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static { MINIO.start(); }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r) {
        r.add("synapse.midia.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add("synapse.midia.url-publica", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add("synapse.midia.bucket", () -> "zapi-entrada-local");
        r.add("synapse.midia.access-key", () -> "zapi-local-access");
        r.add("synapse.midia.secret-key", () -> "zapi-local-secret-storage");
    }
    @TestConfiguration(proxyBeanMethods = false) static class ConfiguracaoLocal {
        @Bean static BeanFactoryPostProcessor storageReal() {
            return f -> { f.getBeanDefinition("armazenamentoDeMidiaFake").setPrimary(false); f.getBeanDefinition("minioArmazenamentoDeMidia").setPrimary(true); };
        }
        // Nao corresponde a protocolo de autenticacao; sequer e registrado como bean em producao.
        @Bean ZApiWebhookTradutor tradutorLocal(ObjectMapper json) { return new ZApiWebhookTradutor(json, INSTANCIA, "WHATSAPP"); }
        @Bean Simulador simulador() { return new Simulador(); }
    }
    static class Simulador implements CanalGateway {
        final AtomicInteger despachos = new AtomicInteger();
        byte[] bytes = new byte[0];
        String mime;
        @Override public String provedor() { return "z-api"; }
        @Override public boolean aceitaTextoLivre(Optional<Instant> ultima, Instant agora) { return true; }
        @Override public boolean exigeTemplateForaDaJanela() { return false; }
        @Override public AutenticacaoDoCanal verificarAutenticacao() { return AutenticacaoDoCanal.aceita(); }
        @Override public ResultadoDeEnvio enviar(Envio envio) {
            despachos.incrementAndGet();
            return new ResultadoDeEnvio.Enfileirado(INSTANCIA, "z-" + envio.mensagemId(), "w-" + envio.mensagemId());
        }
        // Somente fixture local: NAO baixa URL nem simula seguranca SSRF do fornecedor.
        @Override public MidiaRecebida baixarMidiaRecebida(String referencia) { return new MidiaRecebida(bytes, mime); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired WebhookEntrada entrada;
    @Autowired ProcessadorDeWebhookEntrada processador;
    @Autowired PublicadorDaOutbox publicador;
    @Autowired CorrelacionarMensagemDoCanalUseCase correlacionar;
    @Autowired ZApiWebhookTradutor tradutor;
    @Autowired Simulador simulador;
    @Autowired ArmazenamentoDeMidia armazenamento;
    @Autowired ApplicationEvents eventos;
    @Autowired @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) PlatformTransactionManager gerente;
    UUID lead;
    UUID atendimento;
    UUID credencial;
    HttpHeaders headers;

    @BeforeEach void preparar() {
        limpar();
        simulador.despachos.set(0);
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'", UUID.class);
        UUID canal = UUID.randomUUID();
        lead = UUID.randomUUID(); atendimento = UUID.randomUUID(); credencial = UUID.randomUUID();
        jdbc.update("INSERT INTO canal(id,nome,tipo) VALUES (?,?,'WHATSAPP')", canal, PREFIXO + canal);
        jdbc.update("INSERT INTO canal_credencial(id,canal_id,numero,identificador_externo,token_ref,ativo) VALUES (?,?,'5561999990000',?,'secret://teste',true)", credencial, canal, INSTANCIA);
        jdbc.update("INSERT INTO lead(id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?, '5561999990000',?,'EM_ATENDIMENTO',now(),now())", lead, PREFIXO + lead, ana);
        jdbc.update("INSERT INTO atendimento(id,lead_id,canal_credencial_id,atendente_id,status) VALUES (?,?,?,?,'EM_ATENDIMENTO')", atendimento, lead, credencial, ana);
        headers = new HttpHeaders();
        headers.setBearerAuth(ApoioAutenticacao.login(http, ApoioAutenticacao.EMAIL_ANA, ApoioAutenticacao.SENHA_ATENDENTE).accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
    }
    @AfterEach void depois() { limpar(); }

    @Test void webhookRealRecusaAntesDeEscreverMesmoComHeadersOuSegredoPresumidos() {
        headers.set("Client-Token", "nao-e-autenticacao-de-callback");
        headers.set("X-Hub-Signature-256", "sha256=inventada");
        assertThat(http.postForEntity("/webhook/canal?secret=nao-autorizado", new HttpEntity<>(recebido(), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook_entrada", Integer.class)).isZero();
    }

    @Test void filaRealRecebeTextoUmaVezEEmiteEventoParaTempoReal() {
        assertThat(injetar(recebido())).isTrue();
        assertThat(injetar(recebido())).isFalse();
        processador.processarPendentes(); processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?", Integer.class, atendimento)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT conteudo FROM mensagem WHERE atendimento_id=?", String.class, atendimento)).contains("Unicode 🙂");
        assertThat(eventos.stream(MensagemParaTempoReal.class).count()).isEqualTo(1);
    }

    @Test void leituraAntesDoPostFicaDuravelEReconciliadaSemReenvioNemRegressao() {
        UUID mensagem = enviar();
        injetar(status(mensagem, "READ")); processador.processarPendentes();
        assertThat(estado(mensagem)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_correlacao_canal WHERE mensagem_id IS NULL AND instancia=?", Integer.class, INSTANCIA)).isEqualTo(1);
        publicador.publicarPendentes();
        assertThat(estado(mensagem)).isEqualTo("LIDO");
        injetar(status(mensagem, "RECEIVED")); processador.processarPendentes();
        injetar(status(mensagem, "READ")); processador.processarPendentes();
        publicador.publicarPendentes();
        assertThat(estado(mensagem)).isEqualTo("LIDO");
        assertThat(simulador.despachos.get()).isEqualTo(1);
        assertThat(eventos.stream(MudancaDeStatusDeEntrega.class).filter(e -> e.mensagemId().equals(mensagem)).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_correlacao_canal WHERE mensagem_id=?", Integer.class, mensagem)).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void deliveryConfirmaEnvioOuFalhaSemFalsaEntrega(boolean falha) {
        UUID mensagem = enviar(); publicador.publicarPendentes();
        ObjectNode payload = fixture("delivery.json");
        payload.put("messageId", "w-" + mensagem).put("zaapId", "z-" + mensagem);
        if (falha) payload.put("error", "conteudo privado nao deve aparecer na falha");
        injetar(payload.toString()); processador.processarPendentes();
        assertThat(estado(mensagem)).isEqualTo(falha ? "FALHOU" : "ENVIADO");
        if (falha) assertThat(jdbc.queryForObject("SELECT erro_entrega::text FROM mensagem WHERE id=?", String.class, mensagem)).doesNotContain("conteudo privado");
    }

    @ParameterizedTest @ValueSource(strings = {"image", "audio", "video", "document"})
    void recebimentoLocalDeMidiaPersisteBytesNoStoragePrivado(String tipo) throws Exception {
        simulador.bytes = tipo.equals("image") || tipo.equals("document")
                ? Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+juxkAAAAASUVORK5CYII=")
                : new byte[] {1, 2, 3};
        simulador.mime = switch (tipo) { case "audio" -> "audio/ogg"; case "video" -> "video/mp4"; default -> "image/png"; };
        ObjectNode payload = (ObjectNode) json.readTree(recebido());
        payload.remove("text");
        payload.putObject(tipo).put(tipo + "Url", "https://midia.invalid/arquivo").put("mimeType", simulador.mime).put("fileName", "teste.png");
        injetar(payload.toString()); processador.processarPendentes();
        String referencia = jdbc.queryForObject("SELECT midia_url FROM mensagem WHERE atendimento_id=?", String.class, atendimento);
        try (var bytes = armazenamento.abrirLeitura(referencia)) { assertThat(bytes.readAllBytes()).isEqualTo(simulador.bytes); }
        assertThat(jdbc.queryForObject("SELECT tipo::text FROM mensagem WHERE atendimento_id=?", String.class, atendimento))
                .isEqualTo(switch (tipo) { case "image" -> "IMAGEM"; case "audio" -> "AUDIO"; case "video" -> "VIDEO"; default -> "DOCUMENTO"; });
        UUID id = jdbc.queryForObject("SELECT id FROM mensagem WHERE atendimento_id=?", UUID.class, atendimento);
        var download = http.exchange("/api/v1/leads/" + lead + "/midias/" + id + "/download", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isEqualTo(simulador.bytes);
    }

    @Test void instanciaProvedorENamespaceNaoSeMisturamNemAliasPodeSerRoubado() {
        UUID primeira = enviar(); UUID segunda = enviar();
        var chave = new IdentidadeDeMensagemDoCanal("z-api", INSTANCIA, "WHATSAPP", "mesmo-id");
        vincular(chave, primeira);
        var outra = new IdentidadeDeMensagemDoCanal("z-api", "ZAPI-LOCAL-B", "WHATSAPP", "mesmo-id");
        vincular(outra, segunda);
        observar(outra, StatusEntrega.LIDO);
        assertThat(estado(primeira)).isEqualTo("PENDENTE");
        assertThat(estado(segunda)).isEqualTo("LIDO");
        observar(new IdentidadeDeMensagemDoCanal("outro", INSTANCIA, "WHATSAPP", "mesmo-id"), StatusEntrega.LIDO);
        observar(new IdentidadeDeMensagemDoCanal("z-api", INSTANCIA, "PROCESSAMENTO", "mesmo-id"), StatusEntrega.LIDO);
        assertThat(estado(primeira)).isEqualTo("PENDENTE");
        vincular(chave, segunda);
        observar(chave, StatusEntrega.ENVIADO);
        assertThat(estado(primeira)).isEqualTo("ENVIADO");
        assertThat(estado(segunda)).isEqualTo("LIDO");
    }

    @Test void colisaoDeAliasNaoDesfazAceiteNemPermiteOutroDespacho() {
        UUID anterior = enviar(); UUID atual = enviar();
        var alias = new IdentidadeDeMensagemDoCanal("z-api", INSTANCIA, "WHATSAPP", "w-" + atual);
        vincular(alias, anterior);
        publicador.publicarPendentes(); publicador.publicarPendentes();
        assertThat(simulador.despachos.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE publicado_em IS NOT NULL", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT mensagem_id FROM mensagem_correlacao_canal WHERE instancia=? AND namespace='WHATSAPP' AND id_externo=?", UUID.class, INSTANCIA, "w-" + atual)).isEqualTo(anterior);
        assertThat(estado(atual)).isEqualTo("PENDENTE");
    }

    @Test
    void usuarioNaoPodeInjetarStatusPelaChamadaDiretaAoCasoDeUso() {
        UUID ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email='ana@dev.local'", UUID.class);
        com.synapse.crm.app.seguranca.ApoioRls.entrarComo(ana, com.synapse.crm.sharedkernel.identidade.PapelUsuario.ATENDENTE);
        try {
            assertThatThrownBy(() -> correlacionar.executar(List.of(), List.of())).isInstanceOf(AccessDeniedException.class);
        } finally { com.synapse.crm.app.seguranca.ApoioRls.sair(); }
    }

    @Test void rlsNegaAliasAUsuarioDeAplicacaoNaoPrivilegiado() throws Exception {
        UUID mensagem = enviar(); publicador.publicarPendentes();
        try (var conexao = jdbc.getDataSource().getConnection()) {
            conexao.setAutoCommit(false);
            try (var comando = conexao.createStatement()) {
                comando.execute("SET LOCAL ROLE synapse_app");
                comando.execute("SELECT set_config('app.papel','ATENDENTE',true)");
                try (var linhas = comando.executeQuery("SELECT count(*) FROM mensagem_correlacao_canal")) {
                    assertThat(linhas.next()).isTrue(); assertThat(linhas.getInt(1)).isZero();
                }
            } finally { conexao.rollback(); }
        }
        assertThat(estado(mensagem)).isEqualTo("PENDENTE");
    }

    @Test void processadorNaoConsomeBacklogDeOutroProvedor() {
        ContextoDeServico.executarComo("fixture-outro-provedor", () -> new TransactionTemplate(gerente).executeWithoutResult(s ->
                entrada.registrarSeNovo("outro-provedor-local", "uzapi-autotic", recebido(), Instant.now())));
        processador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT processado_em IS NULL FROM webhook_entrada WHERE id_externo='outro-provedor-local'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id=?", Integer.class, atendimento)).isZero();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> estados() {
        return Stream.of(StatusEntrega.values()).flatMap(a -> Stream.of(StatusEntrega.values()).map(b -> org.junit.jupiter.params.provider.Arguments.of(a, b)));
    }
    @ParameterizedTest @MethodSource("estados")
    void sqlConservaMonotoniaDoDominioParaTodosOsPares(StatusEntrega anterior, StatusEntrega novo) {
        UUID mensagem = enviar();
        var chave = new IdentidadeDeMensagemDoCanal("z-api", INSTANCIA, "WHATSAPP", "caso-" + mensagem);
        vincular(chave, mensagem); observar(chave, anterior); observar(chave, novo);
        assertThat(estado(mensagem)).isEqualTo((novo.ehPosteriorA(anterior) ? novo : anterior).name());
    }

    private void vincular(IdentidadeDeMensagemDoCanal chave, UUID mensagem) {
        Instant horario = jdbc.queryForObject("SELECT enviado_em FROM mensagem WHERE id=?", java.sql.Timestamp.class, mensagem).toInstant();
        ContextoDeServico.executarComo("teste-correlacao", () -> correlacionar.executar(List.of(new Vinculo(chave, mensagem, horario, atendimento)), List.of()));
    }
    private void observar(IdentidadeDeMensagemDoCanal chave, StatusEntrega estado) {
        ContextoDeServico.executarComo("teste-status", () -> correlacionar.executar(List.of(), List.of(new StatusComIdentidade(chave, estado, Instant.now()))));
    }
    private boolean injetar(String payload) {
        return ContextoDeServico.buscarComo("fixture-local-sem-endpoint", () -> new TransactionTemplate(gerente).execute(s ->
                entrada.registrarSeNovo(tradutor.idsExternos(payload).getFirst(), "z-api", payload, Instant.now())));
    }
    private UUID enviar() {
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var r = http.postForEntity("/api/v1/atendimentos/mensagens", new HttpEntity<>(Map.of("leadId", lead, "atendimentoId", atendimento, "conteudo", "Teste sintetico"), headers), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return UUID.fromString(r.getBody().get("mensagemId").toString());
    }
    private String estado(UUID id) { return jdbc.queryForObject("SELECT status_entrega::text FROM mensagem WHERE id=?", String.class, id); }
    private String recebido() { return fixture("received.json").put("messageId", "recebida-" + lead).toString(); }
    private String status(UUID mensagem, String estado) {
        ObjectNode payload = fixture("status.json"); payload.put("status", estado); payload.putArray("ids").add("w-" + mensagem); return payload.toString();
    }
    private ObjectNode fixture(String nome) {
        try (var entrada = getClass().getResourceAsStream("/z-api/" + nome)) {
            ObjectNode payload = (ObjectNode) json.readTree(entrada); payload.put("instanceId", INSTANCIA); return payload;
        } catch (java.io.IOException e) { throw new IllegalStateException("FIXTURE_INVALIDA", e); }
    }
    private void limpar() {
        jdbc.update("DELETE FROM webhook_entrada"); jdbc.update("DELETE FROM outbox_evento");
        jdbc.update("DELETE FROM mensagem_correlacao_canal WHERE instancia LIKE 'ZAPI-LOCAL-%'");
        for (String tabela : List.of("evento_timeline", "audit_log")) jdbc.update("DELETE FROM " + tabela + " WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id IN (SELECT a.id FROM atendimento a JOIN lead l ON l.id=a.lead_id WHERE l.nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM canal_credencial WHERE canal_id IN (SELECT id FROM canal WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM canal WHERE nome LIKE ?", PREFIXO + "%");
    }
}
